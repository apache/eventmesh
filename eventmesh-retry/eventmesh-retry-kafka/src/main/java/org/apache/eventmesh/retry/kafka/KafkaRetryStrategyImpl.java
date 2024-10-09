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

package org.apache.eventmesh.retry.kafka;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.api.producer.Producer;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.retry.api.conf.RetryConfiguration;
import org.apache.eventmesh.retry.api.strategy.RetryStrategy;
import java.util.Objects;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KafkaRetryStrategyImpl implements RetryStrategy {

    @Override
    public void retry(RetryConfiguration configuration) {
        sendMessageBack(configuration);
    }

    @SneakyThrows
    private void sendMessageBack(final RetryConfiguration configuration) {
        CloudEvent event = configuration.getEvent();
        String topic = configuration.getTopic();
        String consumerGroupName = configuration.getConsumerGroupName();

        String bizSeqNo = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.BIZSEQNO.getKey())).toString();
        String uniqueId = Objects.requireNonNull(event.getExtension(ProtocolKey.ClientInstanceKey.UNIQUEID.getKey())).toString();
        CloudEvent retryEvent = CloudEventBuilder.from(event)
//            .withExtension(ProtocolKey.TOPIC, topic)
            .withSubject(topic)
            .build();
        Producer producer = configuration.getProducer();
        producer.publish(retryEvent, new SendCallback() {

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
