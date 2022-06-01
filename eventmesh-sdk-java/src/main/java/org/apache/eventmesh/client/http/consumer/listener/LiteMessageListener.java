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

<<<<<<<< HEAD:eventmesh-sdk-java/src/main/java/org/apache/eventmesh/client/tcp/common/ReceiveMsgHook.java
package org.apache.eventmesh.client.tcp.common;

import java.util.Optional;
========
package org.apache.eventmesh.client.http.consumer.listener;


import org.apache.eventmesh.client.http.consumer.HandleResult;
import org.apache.eventmesh.client.http.consumer.context.LiteConsumeContext;
import org.apache.eventmesh.common.LiteMessage;
>>>>>>>> e4cff57da85093ca7a917f7edd86fa434000d5dc:eventmesh-sdk-java/src/main/java/org/apache/eventmesh/client/http/consumer/listener/LiteMessageListener.java

/**
 * ReceiveMsgHook.
 *
 * @param <ProtocolMessage> receive message type.
 */
@FunctionalInterface
public interface ReceiveMsgHook<ProtocolMessage> {

    /**
     * Handle the received message, return the response message.
     *
     * @param msg
     * @return
     */
    Optional<ProtocolMessage> handle(ProtocolMessage msg);

}
