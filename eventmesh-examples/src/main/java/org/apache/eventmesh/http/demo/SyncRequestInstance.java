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

package org.apache.eventmesh.http.demo;

import org.apache.commons.lang3.StringUtils;

import org.apache.eventmesh.client.http.conf.EventMeshHttpClientConfig;
import org.apache.eventmesh.client.http.producer.EventMeshHttpProducer;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.utils.RandomStringUtils;
import org.apache.eventmesh.common.utils.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyncRequestInstance {

    public static Logger logger = LoggerFactory.getLogger(SyncRequestInstance.class);

    public static void main(String[] args) throws Exception {

        EventMeshHttpProducer eventMeshHttpProducer = null;
        String eventMeshIPPort = "127.0.0.1:10105";
        String topic = "EventMesh.SyncRequestInstance";
        try {
            if (args.length > 0 && StringUtils.isNotBlank(args[0])) {
                eventMeshIPPort = args[0];
            }
            if (args.length > 1 && StringUtils.isNotBlank(args[1])) {
                topic = args[1];
            }

            if (StringUtils.isBlank(eventMeshIPPort)) {
                // if has multi value, can config as: 127.0.0.1:10105;127.0.0.2:10105
                eventMeshIPPort = "127.0.0.1:10105";
            }

            EventMeshHttpClientConfig eventMeshClientConfig = new EventMeshHttpClientConfig();
            eventMeshClientConfig.setLiteEventMeshAddr(eventMeshIPPort)
                    .setProducerGroup("EventMeshTest-producerGroup")
                    .setEnv("env")
                    .setIdc("idc")
                    .setIp(IPUtils.getLocalAddress())
                    .setSys("1234")
                    .setPid(String.valueOf(ThreadUtils.getPID()));

            eventMeshHttpProducer = new EventMeshHttpProducer(eventMeshClientConfig);
            eventMeshHttpProducer.start();

            long startTime = System.currentTimeMillis();
            EventMeshMessage eventMeshMessage = new EventMeshMessage();
            eventMeshMessage.setBizSeqNo(RandomStringUtils.generateNum(30))
                    .setContent("contentStr with special protocal")
                    .setTopic(topic)
                    .setUniqueId(RandomStringUtils.generateNum(30));

            EventMeshMessage rsp = eventMeshHttpProducer.request(eventMeshMessage, 10000);
            if (logger.isDebugEnabled()) {
                logger.debug("sendmsg : {}, return : {}, cost:{}ms", eventMeshMessage.getContent(), rsp.getContent(), System.currentTimeMillis() - startTime);
            }
        } catch (Exception e) {
            logger.warn("send msg failed", e);
        }

        try {
            Thread.sleep(30000);
            if (eventMeshHttpProducer != null) {
                eventMeshHttpProducer.shutdown();
            }
        } catch (Exception e1) {
            logger.warn("producer shutdown exception", e1);
        }
    }
}
