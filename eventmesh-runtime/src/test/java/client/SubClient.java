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

<<<<<<<< HEAD:eventmesh-runtime/src/test/java/org/apache/eventmesh/runtime/client/api/SubClient.java
package org.apache.eventmesh.runtime.client.api;
========
package client;

import org.apache.eventmesh.common.protocol.SubcriptionType;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;

import client.hook.ReceiveMsgHook;
>>>>>>>> e4cff57da85093ca7a917f7edd86fa434000d5dc:eventmesh-runtime/src/test/java/client/SubClient.java

import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.runtime.client.hook.ReceiveMsgHook;

/**
 * SubClient
 */
public interface SubClient {
    void init() throws Exception;

    void close();

    void heartbeat() throws Exception;

    void reconnect() throws Exception;

<<<<<<<< HEAD:eventmesh-runtime/src/test/java/org/apache/eventmesh/runtime/client/api/SubClient.java
    Package justSubscribe(String topic, SubscriptionMode subscriptionMode, SubscriptionType subscriptionType) throws Exception;

    Package justUnsubscribe(String topic, SubscriptionMode subscriptionMode, SubscriptionType subscriptionType) throws Exception;
========
    Package justSubscribe(String topic, SubscriptionMode subscriptionMode, SubcriptionType subcriptionType) throws Exception;

    Package justUnsubscribe(String topic, SubscriptionMode subscriptionMode, SubcriptionType subcriptionType) throws Exception;
>>>>>>>> e4cff57da85093ca7a917f7edd86fa434000d5dc:eventmesh-runtime/src/test/java/client/SubClient.java

    Package listen() throws Exception;

    void registerBusiHandler(ReceiveMsgHook handler) throws Exception;

    UserAgent getUserAgent();

    Package goodbye() throws Exception;

}
