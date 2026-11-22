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

package org.apache.eventmesh.storage.rocketmq.patch;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class EventMeshMessageListenerConcurrently implements MessageListenerConcurrently {

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(final List<MessageExt> msgs,
        final ConsumeConcurrentlyContext context) {
        ConsumeConcurrentlyStatus status = null;

        if (CollectionUtils.isEmpty(msgs)) {
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }
        if (msgs.size() != 1) {
            // The patched consume service will resubmit each message separately without acknowledging the batch.
            log.error("EventMesh RocketMQ consumer received {} messages in a batch; expected exactly one", msgs.size());
            ((EventMeshConsumeConcurrentlyContext) context).setRetryIndividually(true);
            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
        }

        MessageExt msg = msgs.get(0);
        try {
            EventMeshConsumeConcurrentlyContext eventMeshConsumeConcurrentlyContext = (EventMeshConsumeConcurrentlyContext) context;
            try {
                EventMeshConsumeConcurrentlyStatus eventMeshConsumeStatus = handleMessage(msg, eventMeshConsumeConcurrentlyContext);
                switch (eventMeshConsumeStatus) {
                    case CONSUME_SUCCESS:
                        eventMeshConsumeConcurrentlyContext.setManualAck(false);
                        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                    case RECONSUME_LATER:
                        return retry(eventMeshConsumeConcurrentlyContext);
                    case CONSUME_FINISH:
                        eventMeshConsumeConcurrentlyContext.setManualAck(true);
                        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                    default:
                        return status;
                }
            } catch (Throwable e) {
                log.error("handleMessage fail", e);
                return retry(eventMeshConsumeConcurrentlyContext);
            }
        } catch (Throwable e) {
            log.error("handleMessage fail", e);
            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
        }
        // return status;
    }

    private ConsumeConcurrentlyStatus retry(EventMeshConsumeConcurrentlyContext context) {
        if (retryLocally()) {
            // Broadcasting has no broker retry; keep the original message until local redelivery succeeds.
            context.setRetryIndividually(true);
        } else {
            context.setManualAck(false);
        }
        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
    }

    protected boolean retryLocally() {
        return false;
    }

    public abstract EventMeshConsumeConcurrentlyStatus handleMessage(MessageExt msg, EventMeshConsumeConcurrentlyContext context);
}
