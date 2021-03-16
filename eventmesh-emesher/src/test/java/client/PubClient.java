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

package client;

import client.hook.ReceiveMsgHook;
import com.webank.eventmesh.common.protocol.tcp.Package;
import com.webank.eventmesh.common.protocol.tcp.UserAgent;


public interface PubClient {

    void init() throws Exception;

    void close();

    void heartbeat() throws Exception;

    void reconnect() throws Exception;

    Package rr(Package msg, long timeout) throws Exception;

    Package publish(Package msg, long timeout) throws Exception;

    Package broadcast(Package msg, long timeout) throws Exception;

    void registerBusiHandler(ReceiveMsgHook handler) throws Exception;

    UserAgent getUserAgent();

    Package dispatcher(Package request, long timeout) throws Exception;

    void goodbye() throws Exception;

    Package askRecommend() throws Exception;

}
