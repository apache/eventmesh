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
//package com.webank.runtime.core.plugin.impl;
//
//import com.webank.runtime.configuration.CommonConfiguration;
//import com.webank.runtime.patch.ProxyConsumeConcurrentlyContext;
//import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
//import org.apache.rocketmq.common.message.MessageExt;
//
//import java.util.List;
//
//public interface MeshMQConsumer {
//
//    void start() throws Exception;
//
//    void updateOffset(List<MessageExt> msgs, ProxyConsumeConcurrentlyContext context);
//
//    void init(boolean isBroadcast, CommonConfiguration commonConfiguration,
//                                  String consumerGroup) throws Exception;
//
//    void registerMessageListener(MessageListenerConcurrently messageListenerConcurrently);
//
//    void subscribe(String topic) throws Exception;
//
//    void unsubscribe(String topic) throws Exception;
//
//    boolean isPause();
//
//    void pause();
//
//    void shutdown() throws Exception;
//
//    void setInstanceName(String instanceName);
//}
