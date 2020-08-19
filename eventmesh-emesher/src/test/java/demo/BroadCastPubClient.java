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

package demo;

import client.common.ClientConstants;
import client.common.MessageUtils;
import client.common.UserAgentUtils;
import client.impl.PubClientImpl;
import com.webank.eventmesh.common.ThreadUtil;

public class BroadCastPubClient {
    public static void main(String[] args) throws Exception {
        PubClientImpl pubClient = new PubClientImpl("127.0.0.1", 10000, UserAgentUtils.createUserAgent());
        pubClient.init();
        pubClient.heartbeat();
        for (int i = 0; i < 10000; i++) {
            ThreadUtil.randomSleep(0, 500);
            pubClient.broadcast(MessageUtils.broadcastMessage(ClientConstants.BROADCAST_TOPIC, i), 5000);
        }
    }
}
