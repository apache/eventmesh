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

package org.apache.eventmesh.runtime.core.protocol.http.consumer;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import io.openmessaging.api.Action;
import io.openmessaging.api.AsyncConsumeContext;
import io.openmessaging.api.AsyncMessageListener;
import io.openmessaging.api.Message;
import io.openmessaging.api.OnExceptionContext;
import io.openmessaging.api.SendCallback;
import io.openmessaging.api.SendResult;

import org.apache.commons.collections4.MapUtils;
import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupConf;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupTopicConf;
import org.apache.eventmesh.runtime.core.plugin.MQConsumerWrapper;
import org.apache.eventmesh.runtime.core.protocol.http.producer.EventMeshProducer;
import org.apache.eventmesh.runtime.core.protocol.http.producer.SendMessageContext;
import org.apache.eventmesh.runtime.core.protocol.http.push.HTTPMessageHandler;
import org.apache.eventmesh.runtime.core.protocol.http.push.MessageHandler;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventMeshConsumer {

    private EventMeshHTTPServer eventMeshHTTPServer;

    private AtomicBoolean started4Persistent = new AtomicBoolean(Boolean.FALSE);

    private AtomicBoolean started4Broadcast = new AtomicBoolean(Boolean.FALSE);

    private AtomicBoolean inited4Persistent = new AtomicBoolean(Boolean.FALSE);

    private AtomicBoolean inited4Broadcast = new AtomicBoolean(Boolean.FALSE);

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    public Logger messageLogger = LoggerFactory.getLogger("message");

    private ConsumerGroupConf consumerGroupConf;

    private MQConsumerWrapper persistentMqConsumer = new MQConsumerWrapper();

    private MQConsumerWrapper broadcastMqConsumer = new MQConsumerWrapper();

    public EventMeshConsumer(EventMeshHTTPServer eventMeshHTTPServer, ConsumerGroupConf consumerGroupConf) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
        this.consumerGroupConf = consumerGroupConf;
    }

    private MessageHandler httpMessageHandler;

    public synchronized void init() throws Exception {
        httpMessageHandler = new HTTPMessageHandler(this);
        Properties keyValue = new Properties();
        keyValue.put("isBroadcast", "false");
        keyValue.put("consumerGroup", consumerGroupConf.getConsumerGroup());
        keyValue.put("eventMeshIDC", eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshIDC);
        keyValue.put("instanceName", EventMeshUtil.buildMeshClientID(consumerGroupConf.getConsumerGroup(),
                eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshRegion,
                eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshCluster));
        persistentMqConsumer.init(keyValue);

        //
        Properties broadcastKeyValue = new Properties();
        broadcastKeyValue.put("isBroadcast", "true");
        broadcastKeyValue.put("consumerGroup", consumerGroupConf.getConsumerGroup());
        broadcastKeyValue.put("eventMeshIDC", eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshIDC);
        broadcastKeyValue.put("instanceName", EventMeshUtil.buildMeshClientID(consumerGroupConf.getConsumerGroup(),
                eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshRegion,
                eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshCluster));
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

    public void subscribe(String topic) throws Exception {
        AsyncMessageListener listener = null;
        if (!EventMeshUtil.isBroadcast(topic)) {
            listener = new AsyncMessageListener() {
                @Override
                public void consume(Message message, AsyncConsumeContext context) {
                    String topic = message.getSystemProperties(Constants.PROPERTY_MESSAGE_DESTINATION);
                    String bizSeqNo = message.getSystemProperties(Constants.PROPERTY_MESSAGE_SEARCH_KEYS);
                    String uniqueId = message.getUserProperties(Constants.RMB_UNIQ_ID);

                    message.getUserProperties().put(EventMeshConstants.REQ_MQ2EVENTMESH_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
                    if (messageLogger.isDebugEnabled()) {
                        messageLogger.debug("message|mq2eventMesh|topic={}|msg={}", topic, message);
                    } else {
                        messageLogger.info("message|mq2eventMesh|topic={}|bizSeqNo={}|uniqueId={}", topic, bizSeqNo, uniqueId);
                    }

                    ConsumerGroupTopicConf currentTopicConfig = MapUtils.getObject(consumerGroupConf.getConsumerGroupTopicConf(), topic, null);

                    if (currentTopicConfig == null) {
                        logger.error("no topicConfig found, consumerGroup:{} topic:{}", consumerGroupConf.getConsumerGroup(), topic);
                        try {
                            sendMessageBack(message, uniqueId, bizSeqNo);
//                            context.attributes().put(NonStandardKeys.MESSAGE_CONSUME_STATUS, EventMeshConsumeConcurrentlyStatus.CONSUME_SUCCESS.name());
//                            context.ack();
                            context.commit(Action.CommitMessage);
                            return;
                        } catch (Exception ex) {
                        }
                    }
                    HandleMsgContext handleMsgContext = new HandleMsgContext(EventMeshUtil.buildPushMsgSeqNo(), consumerGroupConf.getConsumerGroup(), EventMeshConsumer.this,
                            topic, message, persistentMqConsumer.getContext(), consumerGroupConf, eventMeshHTTPServer, bizSeqNo, uniqueId, currentTopicConfig);

                    if (httpMessageHandler.handle(handleMsgContext)) {
//                        context.attributes().put(NonStandardKeys.MESSAGE_CONSUME_STATUS, EventMeshConsumeConcurrentlyStatus.CONSUME_FINISH.name());
//                        context.ack();
                        context.commit(Action.CommitMessage);
                    } else {
                        try {
                            sendMessageBack(message, uniqueId, bizSeqNo);
                        } catch (Exception e) {

                        }
//                        context.attributes().put(NonStandardKeys.MESSAGE_CONSUME_STATUS, EventMeshConsumeConcurrentlyStatus.CONSUME_SUCCESS.name());
//                        context.ack();
                        context.commit(Action.CommitMessage);
                    }
                }
            };
            persistentMqConsumer.subscribe(topic, listener);
        } else {
            listener = new AsyncMessageListener() {
                @Override
                public void consume(Message message, AsyncConsumeContext context) {
                    String topic = message.getSystemProperties(Constants.PROPERTY_MESSAGE_DESTINATION);
                    String bizSeqNo = message.getSystemProperties(Constants.PROPERTY_MESSAGE_SEARCH_KEYS);
                    String uniqueId = message.getUserProperties(Constants.RMB_UNIQ_ID);

                    message.getUserProperties().put(EventMeshConstants.REQ_MQ2EVENTMESH_TIMESTAMP, String.valueOf(System.currentTimeMillis()));

                    if (messageLogger.isDebugEnabled()) {
                        messageLogger.debug("message|mq2eventMesh|topic={}|msg={}", topic, message);
                    } else {
                        messageLogger.info("message|mq2eventMesh|topic={}|bizSeqNo={}|uniqueId={}", topic, bizSeqNo, uniqueId);
                    }

                    ConsumerGroupTopicConf currentTopicConfig = MapUtils.getObject(consumerGroupConf.getConsumerGroupTopicConf(), topic, null);

                    if (currentTopicConfig == null) {
                        logger.error("no topicConfig found, consumerGroup:{} topic:{}", consumerGroupConf.getConsumerGroup(), topic);
                        try {
                            sendMessageBack(message, uniqueId, bizSeqNo);
//                            context.attributes().put(NonStandardKeys.MESSAGE_CONSUME_STATUS, EventMeshConsumeConcurrentlyStatus.CONSUME_SUCCESS.name());
//                            context.ack();
                            context.commit(Action.CommitMessage);
                            return;
                        } catch (Exception ex) {
                        }
                    }
                    HandleMsgContext handleMsgContext = new HandleMsgContext(EventMeshUtil.buildPushMsgSeqNo(), consumerGroupConf.getConsumerGroup(), EventMeshConsumer.this,
                            topic, message, broadcastMqConsumer.getContext(), consumerGroupConf, eventMeshHTTPServer, bizSeqNo, uniqueId, currentTopicConfig);

                    if (httpMessageHandler.handle(handleMsgContext)) {
//                        context.attributes().put(NonStandardKeys.MESSAGE_CONSUME_STATUS, EventMeshConsumeConcurrentlyStatus.CONSUME_FINISH.name());
//                        context.ack();
                        context.commit(Action.CommitMessage);
                    } else {
                        try {
                            sendMessageBack(message, uniqueId, bizSeqNo);
                        } catch (Exception e) {

                        }
//                        context.attributes().put(NonStandardKeys.MESSAGE_CONSUME_STATUS, EventMeshConsumeConcurrentlyStatus.CONSUME_SUCCESS.name());
//                        context.ack();
                        context.commit(Action.CommitMessage);
                    }
                }
            };
            broadcastMqConsumer.subscribe(topic, listener);
        }
    }

    public void unsubscribe(String topic) throws Exception {
        if (EventMeshUtil.isBroadcast(topic)) {
            broadcastMqConsumer.unsubscribe(topic);
        } else {
            persistentMqConsumer.unsubscribe(topic);
        }
    }

//    public boolean isPause() {
//        return persistentMqConsumer.isPause() && broadcastMqConsumer.isPause();
//    }
//
//    public void pause() {
//        persistentMqConsumer.pause();
//        broadcastMqConsumer.pause();
//    }

    public synchronized void shutdown() throws Exception {
        persistentMqConsumer.shutdown();
        started4Persistent.compareAndSet(true, false);
        broadcastMqConsumer.shutdown();
        started4Broadcast.compareAndSet(true, false);
    }

    public void updateOffset(String topic, List<Message> msgs, AbstractContext context) {
        if (EventMeshUtil.isBroadcast(topic)) {
            broadcastMqConsumer.updateOffset(msgs, context);
        } else {
            persistentMqConsumer.updateOffset(msgs, context);
        }
    }

    public ConsumerGroupConf getConsumerGroupConf() {
        return consumerGroupConf;
    }

    public EventMeshHTTPServer getEventMeshHTTPServer() {
        return eventMeshHTTPServer;
    }

    public void sendMessageBack(final Message msgBack, final String uniqueId, String bizSeqNo) throws Exception {

        EventMeshProducer sendMessageBack
                = eventMeshHTTPServer.getProducerManager().getEventMeshProducer(EventMeshConstants.PRODUCER_GROUP_NAME_PREFIX
                + consumerGroupConf.getConsumerGroup());

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

//            @Override
//            public void onException(Throwable e) {
//                logger.warn("consumer:{} consume fail, sendMessageBack, bizSeqno:{}, uniqueId:{}", consumerGroupConf.getConsumerGroup(), bizSeqNo, uniqueId);
//            }
        });
    }
}
