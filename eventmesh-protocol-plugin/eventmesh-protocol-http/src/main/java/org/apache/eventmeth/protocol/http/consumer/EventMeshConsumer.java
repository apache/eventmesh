/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmeth.protocol.http.consumer;

import io.openmessaging.api.AsyncConsumeContext;
import io.openmessaging.api.AsyncMessageListener;
import io.openmessaging.api.Message;
import io.openmessaging.api.OnExceptionContext;
import io.openmessaging.api.SendCallback;
import io.openmessaging.api.SendResult;
import org.apache.commons.collections4.MapUtils;
import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.EventMeshAsyncConsumeContext;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmeth.protocol.http.EventMeshProtocolHTTPServer;
import org.apache.eventmeth.protocol.http.config.HttpProtocolConstants;
import org.apache.eventmeth.protocol.http.handler.HTTPMessageHandler;
import org.apache.eventmeth.protocol.http.handler.MessageHandler;
import org.apache.eventmeth.protocol.http.model.HandleMsgContext;
import org.apache.eventmeth.protocol.http.model.SendMessageContext;
import org.apache.eventmeth.protocol.http.producer.EventMeshProducer;
import org.apache.eventmeth.protocol.http.utils.EventMeshUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

public class EventMeshConsumer {

    private EventMeshProtocolHTTPServer eventMeshHTTPServer;

    private AtomicBoolean started4Persistent = new AtomicBoolean(Boolean.FALSE);

    private AtomicBoolean started4Broadcast = new AtomicBoolean(Boolean.FALSE);

    private AtomicBoolean inited4Persistent = new AtomicBoolean(Boolean.FALSE);

    private AtomicBoolean inited4Broadcast = new AtomicBoolean(Boolean.FALSE);

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    public Logger messageLogger = LoggerFactory.getLogger("message");

    private ConsumerGroupConf consumerGroupConf;

    private MQConsumerWrapper persistentMqConsumer;

    private MQConsumerWrapper broadcastMqConsumer;

    public EventMeshConsumer(EventMeshProtocolHTTPServer eventMeshHTTPServer, ConsumerGroupConf consumerGroupConf) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
        this.consumerGroupConf = consumerGroupConf;
        this.persistentMqConsumer = new MQConsumerWrapper(CommonConfiguration.eventMeshConnectorPluginType);
        this.broadcastMqConsumer = new MQConsumerWrapper(CommonConfiguration.eventMeshConnectorPluginType);
    }

    private MessageHandler httpMessageHandler;

    public synchronized void init() throws Exception {
        httpMessageHandler = new HTTPMessageHandler(this);
        Properties keyValue = new Properties();
        keyValue.put("isBroadcast", "false");
        keyValue.put("consumerGroup", consumerGroupConf.getConsumerGroup());
        keyValue.put("eventMeshIDC", CommonConfiguration.eventMeshIDC);
        keyValue.put("instanceName", EventMeshUtils.buildMeshClientID(consumerGroupConf.getConsumerGroup(), CommonConfiguration.eventMeshCluster));
        persistentMqConsumer.init(keyValue);

        Properties broadcastKeyValue = new Properties();
        broadcastKeyValue.put("isBroadcast", "true");
        broadcastKeyValue.put("consumerGroup", consumerGroupConf.getConsumerGroup());
        broadcastKeyValue.put("eventMeshIDC", CommonConfiguration.eventMeshIDC);
        broadcastKeyValue.put("instanceName", EventMeshUtils.buildMeshClientID(consumerGroupConf.getConsumerGroup(), CommonConfiguration.eventMeshCluster));
        broadcastMqConsumer.init(broadcastKeyValue);
        inited4Persistent.compareAndSet(false, true);
        inited4Broadcast.compareAndSet(false, true);
        logger.info("EventMeshConsumer [{}] inited.............", consumerGroupConf.getConsumerGroup());
    }

    public synchronized void start() throws Exception {
        persistentMqConsumer.start();
        started4Persistent.compareAndSet(false, true);
        broadcastMqConsumer.start();
        started4Broadcast.compareAndSet(false, true);
    }

    public void subscribe(String topic, SubscriptionItem subscriptionItem) throws Exception {
        AsyncMessageListener listener = null;
        if (!SubscriptionMode.BROADCASTING.equals(subscriptionItem.getMode())) {
            listener = (message, context) -> {
                String topic12 = message.getSystemProperties(Constants.PROPERTY_MESSAGE_DESTINATION);
                String bizSeqNo = message.getSystemProperties(Constants.PROPERTY_MESSAGE_SEARCH_KEYS);
                String uniqueId = message.getUserProperties(Constants.RMB_UNIQ_ID);

                message.getUserProperties().put(HttpProtocolConstants.REQ_MQ2EVENTMESH_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
                if (messageLogger.isDebugEnabled()) {
                    messageLogger.debug("message|mq2eventMesh|topic={}|msg={}", topic12, message);
                } else {
                    messageLogger.info("message|mq2eventMesh|topic={}|bizSeqNo={}|uniqueId={}", topic12, bizSeqNo, uniqueId);
                }

                ConsumerGroupTopicConf currentTopicConfig = MapUtils.getObject(consumerGroupConf.getConsumerGroupTopicConf(), topic12, null);
                EventMeshAsyncConsumeContext eventMeshAsyncConsumeContext = (EventMeshAsyncConsumeContext)context;

                if (currentTopicConfig == null) {
                    logger.error("no topicConfig found, consumerGroup:{} topic:{}", consumerGroupConf.getConsumerGroup(), topic12);
                    try {
                        sendMessageBack(message, uniqueId, bizSeqNo);
                        eventMeshAsyncConsumeContext.commit(EventMeshAction.CommitMessage);
                        return;
                    } catch (Exception ex) {
                    }
                }
                HandleMsgContext handleMsgContext = new HandleMsgContext(EventMeshUtils.buildPushMsgSeqNo(), consumerGroupConf.getConsumerGroup(), EventMeshConsumer.this,
                        topic12, message, subscriptionItem, eventMeshAsyncConsumeContext.getAbstractContext(), consumerGroupConf, eventMeshHTTPServer, bizSeqNo, uniqueId, currentTopicConfig);

                if (httpMessageHandler.handle(handleMsgContext)) {
                    eventMeshAsyncConsumeContext.commit(EventMeshAction.ManualAck);
                } else {
                    try {
                        sendMessageBack(message, uniqueId, bizSeqNo);
                    } catch (Exception e) {

                    }
                    eventMeshAsyncConsumeContext.commit(EventMeshAction.CommitMessage);
                }
            };
            persistentMqConsumer.subscribe(topic, listener);
        } else {
            listener = (message, context) -> {
                String topic1 = message.getSystemProperties(Constants.PROPERTY_MESSAGE_DESTINATION);
                String bizSeqNo = message.getSystemProperties(Constants.PROPERTY_MESSAGE_SEARCH_KEYS);
                String uniqueId = message.getUserProperties(Constants.RMB_UNIQ_ID);

                message.getUserProperties().put(HttpProtocolConstants.REQ_MQ2EVENTMESH_TIMESTAMP, String.valueOf(System.currentTimeMillis()));

                if (messageLogger.isDebugEnabled()) {
                    messageLogger.debug("message|mq2eventMesh|topic={}|msg={}", topic1, message);
                } else {
                    messageLogger.info("message|mq2eventMesh|topic={}|bizSeqNo={}|uniqueId={}", topic1, bizSeqNo, uniqueId);
                }

                ConsumerGroupTopicConf currentTopicConfig = MapUtils.getObject(consumerGroupConf.getConsumerGroupTopicConf(), topic1, null);
                EventMeshAsyncConsumeContext eventMeshAsyncConsumeContext = (EventMeshAsyncConsumeContext)context;

                if (currentTopicConfig == null) {
                    logger.error("no topicConfig found, consumerGroup:{} topic:{}", consumerGroupConf.getConsumerGroup(), topic1);
                    try {
                        sendMessageBack(message, uniqueId, bizSeqNo);
                        eventMeshAsyncConsumeContext.commit(EventMeshAction.CommitMessage);
                        return;
                    } catch (Exception ex) {
                    }
                }
                HandleMsgContext handleMsgContext = new HandleMsgContext(EventMeshUtils.buildPushMsgSeqNo(), consumerGroupConf.getConsumerGroup(), EventMeshConsumer.this,
                        topic1, message, subscriptionItem, eventMeshAsyncConsumeContext.getAbstractContext(), consumerGroupConf, eventMeshHTTPServer, bizSeqNo, uniqueId, currentTopicConfig);

                if (httpMessageHandler.handle(handleMsgContext)) {
                    eventMeshAsyncConsumeContext.commit(EventMeshAction.ManualAck);
                } else {
                    try {
                        sendMessageBack(message, uniqueId, bizSeqNo);
                    } catch (Exception e) {

                    }
                    eventMeshAsyncConsumeContext.commit(EventMeshAction.CommitMessage);
                }
            };
            broadcastMqConsumer.subscribe(topic, listener);
        }
    }

    public void unsubscribe(String topic, SubscriptionMode subscriptionMode) throws Exception {
        if (SubscriptionMode.BROADCASTING.equals(subscriptionMode)) {
            broadcastMqConsumer.unsubscribe(topic);
        } else {
            persistentMqConsumer.unsubscribe(topic);
        }
    }

    public synchronized void shutdown() throws Exception {
        persistentMqConsumer.shutdown();
        started4Persistent.compareAndSet(true, false);
        broadcastMqConsumer.shutdown();
        started4Broadcast.compareAndSet(true, false);
    }

    public void updateOffset(String topic, SubscriptionMode subscriptionMode, List<Message> msgs, AbstractContext context) {
        if (SubscriptionMode.BROADCASTING.equals(subscriptionMode)) {
            broadcastMqConsumer.updateOffset(msgs, context);
        } else {
            persistentMqConsumer.updateOffset(msgs, context);
        }
    }

    public ConsumerGroupConf getConsumerGroupConf() {
        return consumerGroupConf;
    }

    public EventMeshProtocolHTTPServer getEventMeshHTTPServer() {
        return eventMeshHTTPServer;
    }

    public void sendMessageBack(final Message msgBack, final String uniqueId, String bizSeqNo) throws Exception {

        EventMeshProducer sendMessageBack
                = eventMeshHTTPServer.getProducerManager().getEventMeshProducer(consumerGroupConf.getConsumerGroup());

        if (sendMessageBack == null) {
            logger.warn("consumer:{} consume fail, sendMessageBack, bizSeqNo:{}, uniqueId:{}", consumerGroupConf.getConsumerGroup(), bizSeqNo, uniqueId);
            return;
        }

        final SendMessageContext sendMessageBackContext = new SendMessageContext(bizSeqNo, msgBack, sendMessageBack, eventMeshHTTPServer);

        sendMessageBack.send(sendMessageBackContext, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
            }

            @Override
            public void onException(OnExceptionContext context) {
                logger.warn("consumer:{} consume fail, sendMessageBack, bizSeqno:{}, uniqueId:{}", consumerGroupConf.getConsumerGroup(), bizSeqNo, uniqueId);
            }
        });
    }
}
