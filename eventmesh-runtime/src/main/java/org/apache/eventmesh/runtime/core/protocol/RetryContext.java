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

package org.apache.eventmesh.runtime.core.protocol;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.TopicNameHelper;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupConf;
import org.apache.eventmesh.runtime.core.protocol.consumer.HandleMessageContext;
import org.apache.eventmesh.runtime.core.protocol.producer.EventMeshProducer;
import org.apache.eventmesh.runtime.core.protocol.producer.ProducerManager;
import org.apache.eventmesh.runtime.core.protocol.producer.SendMessageContext;
import org.apache.eventmesh.runtime.core.timer.Timeout;
import org.apache.eventmesh.runtime.core.timer.Timer;
import org.apache.eventmesh.runtime.core.timer.TimerTask;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class RetryContext implements TimerTask {

    public CloudEvent event;

    public String seq;

    public int retryTimes;

    private static final Map<String, Integer> STORAGE_RETRY_TIMES_MAP = new ConcurrentHashMap<>();

    public CommonConfiguration commonConfiguration;

    public long executeTime = System.currentTimeMillis();

    public long getExecuteTime() {
        return executeTime;
    }

    protected void rePut(Timeout timeout, long tick, TimeUnit timeUnit) {
        if (timeout == null) {
            return;
        }

        Timer timer = timeout.timer();
        if (timer.isStop() || timeout.isCancelled()) {
            return;
        }

        timer.newTimeout(timeout.task(), tick, timeUnit);
    }

    public void setEvent(CloudEvent event) {
        this.event = event;
    }

    @Override
    public void setExecuteTimeHook(long executeTime) {
        this.executeTime = executeTime;
    }

    @Override
    public final void run(Timeout timeout) throws Exception {
        boolean eventMeshServerRetryStorageEnabled = commonConfiguration.isEventMeshServerRetryStorageEnabled();
        if (eventMeshServerRetryStorageEnabled) {
            if (!STORAGE_RETRY_TIMES_MAP.containsKey(event.getId())) {
                STORAGE_RETRY_TIMES_MAP.put(event.getId(), 0);
            }
            Integer maxTimesPerEvent = STORAGE_RETRY_TIMES_MAP.get(event.getId());
            if (maxTimesPerEvent < 1) {
                sendMessageBack(event);
                STORAGE_RETRY_TIMES_MAP.put(event.getId(), STORAGE_RETRY_TIMES_MAP.get(event.getId()) + 1);
            } else {
                getHandleMessageContext().finish();
            }
        } else {
            doRun(timeout);
        }
    }

    protected HandleMessageContext getHandleMessageContext() throws Exception {
        throw new IllegalAccessException("method not supported.");
    }

    public abstract void doRun(Timeout timeout) throws Exception;

    @SneakyThrows
    protected ProducerManager getProducerManager() {
        throw new IllegalAccessException("method not supported.");
    }

    @SneakyThrows
    protected ConsumerGroupConf getConsumerGroupConf() {
        throw new IllegalAccessException("method not supported.");
    }

    private void sendMessageBack(final CloudEvent event) throws Exception {
        String topic = event.getSubject();
        String bizSeqNo = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.BIZSEQNO.getKey())).toString();
        String uniqueId = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.UNIQUEID.getKey())).toString();

        ConsumerGroupConf consumerGroupConf = getConsumerGroupConf();
        String consumerGroupName = consumerGroupConf.getConsumerGroup();
        EventMeshProducer sendMessageBack = getProducerManager().getEventMeshProducer(consumerGroupName);

        if (sendMessageBack == null) {
            log.warn("consumer:{} consume fail, sendMessageBack, topic:{}, bizSeqNo:{}, uniqueId:{}",
                consumerGroupName, topic, bizSeqNo, uniqueId);
            return;
        }
        TopicNameHelper topicNameGenerator = EventMeshExtensionFactory.getExtension(TopicNameHelper.class,
            commonConfiguration.getEventMeshStoragePluginType());
        String retryTopicName = topicNameGenerator.generateRetryTopicName(consumerGroupName);
        CloudEvent retryEvent = CloudEventBuilder.from(event)
            .withExtension(ProtocolKey.TOPIC, consumerGroupConf.getTopic())
            .withSubject(retryTopicName)
            .build();

        final SendMessageContext sendMessageBackContext = new SendMessageContext(bizSeqNo, retryEvent, sendMessageBack, null);

        sendMessageBack.send(sendMessageBackContext, new SendCallback() {

            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("consumer:{} consume success,, bizSeqno:{}, uniqueId:{}",
                    consumerGroupName, bizSeqNo, uniqueId);
            }

            @Override
            public void onException(OnExceptionContext context) {
                log.warn("consumer:{} consume fail, sendMessageBack, bizSeqno:{}, uniqueId:{}",
                    consumerGroupName, bizSeqNo, uniqueId, context.getException());
            }
        });
    }
}
