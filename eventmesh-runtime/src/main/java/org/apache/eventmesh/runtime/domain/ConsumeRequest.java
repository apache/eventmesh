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
//package com.webank.runtime.domain;
//
//import org.apache.rocketmq.client.impl.consumer.ProcessQueue;
//import org.apache.rocketmq.common.message.MessageExt;
//import org.apache.rocketmq.common.message.MessageQueue;
//
//public class ConsumeRequest {
//    private final MessageExt messageExt;
//    private final MessageQueue messageQueue;
//    private final ProcessQueue processQueue;
//    private long startConsumeTimeMillis;
//
//    public ConsumeRequest(final MessageExt messageExt, final MessageQueue messageQueue,
//        final ProcessQueue processQueue) {
//        this.messageExt = messageExt;
//        this.messageQueue = messageQueue;
//        this.processQueue = processQueue;
//    }
//
//    public MessageExt getMessageExt() {
//        return messageExt;
//    }
//
//    public MessageQueue getMessageQueue() {
//        return messageQueue;
//    }
//
//    public ProcessQueue getProcessQueue() {
//        return processQueue;
//    }
//
//    public long getStartConsumeTimeMillis() {
//        return startConsumeTimeMillis;
//    }
//
//    public void setStartConsumeTimeMillis(final long startConsumeTimeMillis) {
//        this.startConsumeTimeMillis = startConsumeTimeMillis;
//    }
//}
