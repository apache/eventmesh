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

package org.apache.eventmesh.client.http.demo;

import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.client.http.conf.LiteClientConfig;
import org.apache.eventmesh.client.http.producer.LiteProducer;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.utils.RandomStringUtils;
import org.apache.eventmesh.common.utils.IPUtil;
import org.apache.eventmesh.common.LiteMessage;
import org.apache.eventmesh.common.utils.ThreadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncPublishInstance {

    public static Logger logger = LoggerFactory.getLogger(AsyncPublishInstance.class);

    public static void main(String[] args) throws Exception {

        LiteProducer liteProducer = null;
        try {
//            String eventMeshIPPort = args[0];
            String eventMeshIPPort = "";
//            final String topic = args[1];
            final String topic = "TEST-TOPIC-HTTP-ASYNC";
            if (StringUtils.isBlank(eventMeshIPPort)) {
                // if has multi value, can config as: 127.0.0.1:10105;127.0.0.2:10105
                eventMeshIPPort = "127.0.0.1:10105";
            }

            LiteClientConfig eventMeshClientConfig = new LiteClientConfig();
            eventMeshClientConfig.setLiteEventMeshAddr(eventMeshIPPort)
                    .setProducerGroup("EventMeshTest-producerGroup")
                    .setEnv("env")
                    .setIdc("idc")
                    .setIp(IPUtil.getLocalAddress())
                    .setSys("1234")
                    .setPid(String.valueOf(ThreadUtil.getPID()));

            liteProducer = new LiteProducer(eventMeshClientConfig);
            liteProducer.start();
            for (int i = 0; i < 1; i++) {
                LiteMessage liteMessage = new LiteMessage();
                liteMessage.setBizSeqNo(RandomStringUtils.generateNum(30))
//                    .setContent("contentStr with special protocal")
                        .setContent("testPublishMessage")
                        .setTopic(topic)
                        .setUniqueId(RandomStringUtils.generateNum(30))
                        .addProp(Constants.EVENTMESH_MESSAGE_CONST_TTL, String.valueOf(4 * 1000));

                boolean flag = liteProducer.publish(liteMessage);
                Thread.sleep(1000);
                logger.info("publish result , {}", flag);
            }
        } catch (Exception e) {
            logger.warn("publish msg failed", e);
        }

        try {
            Thread.sleep(30000);
            if (liteProducer != null) {
                liteProducer.shutdown();
            }
        } catch (Exception e1) {
            logger.warn("producer shutdown exception", e1);
        }
    }
}
