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

package org.apache.eventmesh.connector.kafka.producer;

import org.apache.eventmesh.connector.kafka.common.Constants;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Properties;

public class OMSProducerAdaptorTest {

    private OMSProducerAdaptor omsProducerAdaptor;

    @Before
    public void before() {
        String path = this.getClass().getClassLoader().getResource(Constants.KAFKA_CONF_FILE).getPath();
        String substring = path.substring(0, path.lastIndexOf(File.separator));
        System.setProperty("confPath", substring);

        Properties properties = new Properties();
        properties.put("producerGroup", "testGroup");
        omsProducerAdaptor = new OMSProducerAdaptor(properties);
    }

    @Test
    public void init() throws Exception {
        omsProducerAdaptor.init(null);
        // no exception, assert true
        Assert.assertTrue(true);
    }

    @Test
    public void isStarted() {
    }

    @Test
    public void isClosed() {
    }

    @Test
    public void start() {
    }

    @Test
    public void send() {
    }

    @Test
    public void request() {
    }

    @Test
    public void testRequest() {
    }

    @Test
    public void reply() {
    }

    @Test
    public void getMeshMQProducer() {
    }

    @Test
    public void buildMQClientId() {
    }

    @Test
    public void setExtFields() {
    }

    @Test
    public void getDefaultTopicRouteInfoFromNameServer() {
    }

    @Test
    public void shutdown() {
    }

    @Test
    public void testSend() {
    }

    @Test
    public void sendOneway() {
    }

    @Test
    public void sendAsync() {
    }

    @Test
    public void setCallbackExecutor() {
    }

    @Test
    public void updateCredential() {
    }

    @Test
    public void messageBuilder() {
    }
}