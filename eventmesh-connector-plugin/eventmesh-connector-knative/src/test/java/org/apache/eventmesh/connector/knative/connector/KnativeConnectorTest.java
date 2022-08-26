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

package org.apache.eventmesh.connector.knative.connector;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.common.config.ConfigurationWrapper;
import org.apache.eventmesh.common.utils.RandomStringUtils;
import org.apache.eventmesh.connector.knative.cloudevent.KnativeMessageFactory;
import org.apache.eventmesh.connector.knative.cloudevent.impl.KnativeHeaders;
import org.apache.eventmesh.runtime.boot.EventMeshServer;
import org.apache.eventmesh.runtime.configuration.EventMeshGrpcConfiguration;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;
import org.apache.eventmesh.runtime.configuration.EventMeshTCPConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.consumergroup.ProducerGroupConf;
import org.apache.eventmesh.runtime.core.protocol.http.producer.EventMeshProducer;
import org.apache.eventmesh.runtime.core.protocol.http.producer.SendMessageContext;

import java.util.Properties;

import org.junit.Test;

public class KnativeConnectorTest {

    @Test
    public void testConnect() throws Exception {
        // Start an EventMesh server:
        ConfigurationWrapper configurationWrapper =
            new ConfigurationWrapper(System.getProperty("user.dir") + "/../../eventmesh-runtime/conf/",
                EventMeshConstants.EVENTMESH_CONF_FILE, false);

        EventMeshHTTPConfiguration eventMeshHttpConfiguration = new EventMeshHTTPConfiguration(configurationWrapper);
        eventMeshHttpConfiguration.init();
        EventMeshTCPConfiguration eventMeshTcpConfiguration = new EventMeshTCPConfiguration(configurationWrapper);
        eventMeshTcpConfiguration.init();
        EventMeshGrpcConfiguration eventMeshGrpcConfiguration = new EventMeshGrpcConfiguration(configurationWrapper);
        eventMeshGrpcConfiguration.init();

        EventMeshServer server = new EventMeshServer(eventMeshHttpConfiguration, eventMeshTcpConfiguration, eventMeshGrpcConfiguration);
        server.init();
        server.start();

        // Create a Knative producer:
        EventMeshProducer eventMeshProducer =
            server.eventMeshHTTPServer.getProducerManager().createEventMeshProducer(new ProducerGroupConf("test-producer-group"));

        // Publish an event message:
        Properties properties = new Properties();

        properties.put(KnativeHeaders.CONTENT_TYPE, "application/json");
        properties.put(KnativeHeaders.CE_ID, "1234");
        properties.put(KnativeHeaders.CE_SPECVERSION, "1.0");
        properties.put(KnativeHeaders.CE_TYPE, "some-type");
        properties.put(KnativeHeaders.CE_SOURCE, "java-client");
        properties.put("data", "Hello Knative from EventMesh!");
        SendMessageContext sendMessageContext =
            new SendMessageContext(RandomStringUtils.generateNum(30), KnativeMessageFactory.createWriter(properties), eventMeshProducer,
                server.eventMeshHTTPServer);

        eventMeshProducer.send(sendMessageContext, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
            }

            @Override
            public void onException(OnExceptionContext context) {
            }
        });

        // Shutdown EventMesh server:
        server.shutdown();
    }
}
