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

package com.webank.eventmesh.client.tcp;


import com.webank.eventmesh.client.tcp.common.ReceiveMsgHook;
import com.webank.eventmesh.common.protocol.tcp.UserAgent;

public interface SimpleSubClient {
     void init() throws Exception;

     void close();

     void heartbeat() throws Exception;

     void reconnect() throws Exception;

     void subscribe(String topic) throws Exception;

     void unsubscribe() throws Exception;

     void listen() throws Exception;

     void registerBusiHandler(ReceiveMsgHook handler) throws Exception;

     UserAgent getUserAgent();
}
