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

package org.apache.eventmesh.runtime.it;


import org.apache.rocketmq.common.protocol.route.TopicRouteData;
import org.apache.rocketmq.remoting.netty.NettyClientConfig;
import org.apache.rocketmq.remoting.netty.NettyRemotingClient;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "it.storage", matches = "rocketmq|kafka")
class BrokerListTest {

    @Test
    void listBrokers() throws Exception {
        String namesrv = System.getProperty("it.namesrv", "localhost:9876");
        NettyRemotingClient client = new NettyRemotingClient(new NettyClientConfig());
        client.start();

        // GET_BROKER_CLUSTER_INFO = 106
        RemotingCommand request = RemotingCommand.createRequestCommand(106, null);
        RemotingCommand response = client.invokeSync(namesrv, request, 5000);

        if (response.getBody() != null) {
            String json = new String(response.getBody(), "UTF-8");
        }

        // Also try fetching route for a test topic
        org.apache.rocketmq.common.protocol.header.namesrv.GetRouteInfoRequestHeader header =
            new org.apache.rocketmq.common.protocol.header.namesrv.GetRouteInfoRequestHeader();
        header.setTopic("TBW102"); // default topic
        RemotingCommand routeReq = RemotingCommand.createRequestCommand(105, header);
        RemotingCommand routeResp = client.invokeSync(namesrv, routeReq, 5000);
        if (routeResp.getBody() != null) {
            TopicRouteData route = org.apache.rocketmq.remoting.protocol.RemotingSerializable.decode(
                routeResp.getBody(), TopicRouteData.class);
            for (var qd : route.getQueueDatas()) {
            }
            for (var bd : route.getBrokerDatas()) {
            }
        }
        client.shutdown();
    }
}


