///*
// * Licensed to the Apache Software Foundation (ASF) under one or more
// * contributor license agreements.  See the NOTICE file distributed with
// * this work for additional information regarding copyright ownership.
// * The ASF licenses this file to You under the Apache License, Version 2.0
// * (the "License"); you may not use this file except in compliance with
// * the License.  You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package org.apache.eventmesh.runtime.patch;
//
//import org.apache.commons.collections4.CollectionUtils;
//import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
//import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
//import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
//import org.apache.rocketmq.common.message.MessageExt;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.util.List;
//
//public abstract class EventMeshMessageListenerConcurrently implements MessageListenerConcurrently {
//
//    private static final Logger LOG = LoggerFactory.getLogger(EventMeshMessageListenerConcurrently.class);
//
//    @Override
//    public ConsumeConcurrentlyStatus consumeMessage(final List<MessageExt> msgs,
//                                                    final ConsumeConcurrentlyContext context) {
//        ConsumeConcurrentlyStatus status = null;
//
//        if (CollectionUtils.isEmpty(msgs)) {
//            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
//        }
//
//        MessageExt msg = msgs.get(0);
//        try {
//            EventMeshConsumeConcurrentlyContext eventMeshConsumeConcurrentlyContext = (EventMeshConsumeConcurrentlyContext) context;
//            EventMeshConsumeConcurrentlyStatus eventMeshConsumeStatus = handleMessage(msg, eventMeshConsumeConcurrentlyContext);
//            try {
//                switch (eventMeshConsumeStatus) {
//                    case CONSUME_SUCCESS:
//                        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
//                    case RECONSUME_LATER:
//                        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
//                    case CONSUME_FINISH:
//                        eventMeshConsumeConcurrentlyContext.setManualAck(true);
//                        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
//                }
//            } catch (Throwable e) {
//                LOG.info("handleMessage fail", e);
//                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
//            }
//        } catch (Throwable e) {
//            LOG.info("handleMessage fail", e);
//            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
//        }
//        return status;
//    }
//
//    public abstract EventMeshConsumeConcurrentlyStatus handleMessage(MessageExt msg, EventMeshConsumeConcurrentlyContext context);
//}
