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

package org.apache.eventmesh.connector.dledger;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.connector.dledger.broker.DLedgerTopicIndexesStore;

import java.net.URI;
import java.util.Properties;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

public class DLedgerProducerTest {

    private DLedgerConnectorResourceService resourceService;
    private DLedgerProducer producer;


    @Before
    public void setUp() throws Exception {
        resourceService = new DLedgerConnectorResourceService();
        resourceService.init();

        producer = new DLedgerProducer();
        producer.init(new Properties());
        producer.start();
    }

    @After
    public void tearDown() throws Exception {
        producer.shutdown();
        resourceService.release();
    }

    @Test
    public void publish() throws Exception {
        String data = "{\"headers\":{\"content-length\":\"36\",\"Accept\":\"*/*\",\"ip\":\"127.0.0.1:51226\",\"User-Agent\":\"curl/7.83.1\"," +
            "\"Host\":\"127.0.0.1:10105\",\"source\":\"127.0.0.1:51226\",\"Content-Type\":\"application/json\"}," +
            "\"path\":\"/eventmesh/publish/TEST-TOPIC-HTTP-ASYNC\",\"method\":\"POST\",\"body\":{\"pass\":\"12345678\",\"name\":\"admin\"}}";
        CloudEvent cloudEvent = CloudEventBuilder.v1()
                                                 .withId("c61039e1-7884-4d7f-b72f-6c61160a64fc")
                                                 .withSource(new URI("source:127.0.0.1:51226"))
                                                 .withType("http_request")
                                                 .withDataContentType("application/json")
                                                 .withSubject("TEST-TOPIC-HTTP-ASYNC")
                                                 .withData(data.getBytes())
                                                 .withExtension("reqeventmesh2mqtimestamp", "1659342713460")
                                                 .withExtension("ip", "127.0.0.1:51226")
                                                 .withExtension("idc", "idc")
                                                 .withExtension("protocoldesc", "http")
                                                 .withExtension("pid", 2376)
                                                 .withExtension("env", "env")
                                                 .withExtension("sys", 1234)
                                                 .withExtension("ttl", 4000)
                                                 .withExtension("producergroup", "em-http-producer")
                                                 .withExtension("consumergroup", "em-http-consumer")
                                                 .withExtension("passwd", "pass")
                                                 .withExtension("bizseqno", "249695004068274968410952665702")
                                                 .withExtension("protocoltype", "http")
                                                 .withExtension("msgtype", "persistent")
                                                 .withExtension("uniqueid", "866012286651006371403062105469")
                                                 .withExtension("username", "eventmesh")
                                                 .withExtension("reqc2eventmeshtimestamp", "1659342713460")
                                                 .build();
        producer.publish(cloudEvent, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                Assert.assertTrue(DLedgerTopicIndexesStore.getInstance().contain("TEST-TOPIC-HTTP-ASYNC"));
                System.out.println(sendResult);
            }

            @Override
            public void onException(OnExceptionContext context) {
                Assert.assertEquals(
                    "org.apache.eventmesh.connector.dledger.exception.DLedgerConnectorException: Error code: 500",
                    context.getException().getMessage());
            }
        });
    }

    @Test
    public void sendOneway() {
    }

    @Test
    public void request() {
    }

    @Test
    public void reply() {
    }

    @Test
    public void checkTopicExist() {
    }

    @Test
    public void setExtFields() {
    }
}