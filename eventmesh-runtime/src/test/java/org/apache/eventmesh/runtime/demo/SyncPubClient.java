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

package org.apache.eventmesh.runtime.demo;

import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.runtime.client.common.MessageUtils;
import org.apache.eventmesh.runtime.client.common.UserAgentUtils;
import org.apache.eventmesh.runtime.client.impl.PubClientImpl;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SyncPubClient {

    public static void main(String[] args) throws Exception {
        try (PubClientImpl pubClient =
            new PubClientImpl("localhost", 10000, UserAgentUtils.createUserAgent())) {
            pubClient.init();
            pubClient.heartbeat();

            for (int i = 0; i < 100; i++) {
                Package rr = pubClient.rr(MessageUtils.rrMesssage("TEST-TOPIC-TCP-SYNC", i), 3000);
                if (rr.getBody() instanceof EventMeshMessage) {
                    String body = ((EventMeshMessage) rr.getBody()).getBody();
                    if (log.isInfoEnabled()) {
                        log.info("rrMessage: " + body + "             "
                            + "rr-reply-------------------------------------------------" + rr);
                    }
                }
            }
        }
    }
}
