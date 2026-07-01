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

package org.apache.eventmesh.connector.openfunction.server;

import org.apache.eventmesh.connector.openfunction.config.OpenFunctionServerConfig;
import org.apache.eventmesh.connector.openfunction.service.ConsumerService;
import org.apache.eventmesh.connector.openfunction.service.ProducerService;
import org.apache.eventmesh.connector.openfunction.sink.connector.OpenFunctionSinkConnector;
import org.apache.eventmesh.connector.openfunction.source.connector.OpenFunctionSourceConnector;
import org.apache.eventmesh.openconnect.Application;
import org.apache.eventmesh.openconnect.api.connector.Connector;
import org.apache.eventmesh.openconnect.util.ConfigUtil;

import java.util.Map;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OpenFunctionConnectServer {

    private static Server server;

    public static void main(String[] args) throws Exception {

        OpenFunctionServerConfig serverConfig = ConfigUtil.parse(OpenFunctionServerConfig.class, "server-config.yml");

        int serverPort = serverConfig.getServerPort();

        ServerBuilder<?> grpcServerBuilder = ServerBuilder.forPort(serverPort);

        if (serverConfig.isSourceEnable()) {
            Application openFunctionSourceApp = new Application();
            openFunctionSourceApp.run(OpenFunctionSourceConnector.class);
        }

        if (serverConfig.isSinkEnable()) {
            Application openFunctionSinkApp = new Application();
            openFunctionSinkApp.run(OpenFunctionSinkConnector.class);
        }

        Map<String, Connector> connectorMap = Application.CONNECTOR_MAP;

        for (Map.Entry<String, Connector> entry : connectorMap.entrySet()) {
            if (Application.isSource(entry.getValue().getClass())) {
                grpcServerBuilder.addService(new ProducerService((OpenFunctionSourceConnector) entry.getValue(), serverConfig));
            } else if (Application.isSink(entry.getValue().getClass())) {
                grpcServerBuilder.addService(new ConsumerService((OpenFunctionSinkConnector) entry.getValue(), serverConfig));
            }
        }
        server = grpcServerBuilder.build();
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.shutdown();
            } catch (Exception e) {
                log.error("exception when shutdown.", e);
            }
        }));
        server.awaitTermination();
    }
}
