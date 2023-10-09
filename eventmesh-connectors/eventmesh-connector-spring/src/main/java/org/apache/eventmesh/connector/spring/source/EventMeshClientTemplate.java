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

package org.apache.eventmesh.connector.spring.source;

import org.apache.eventmesh.client.IProducer;
import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.client.grpc.producer.EventMeshGrpcProducer;
import org.apache.eventmesh.client.http.conf.EventMeshHttpClientConfig;
import org.apache.eventmesh.client.http.producer.EventMeshHttpProducer;
import org.apache.eventmesh.client.tcp.EventMeshTcpClientAdapter;
import org.apache.eventmesh.client.tcp.conf.EventMeshTCPClientConfig;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.enums.ProtocolType;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.ThreadUtils;
import org.apache.eventmesh.connector.spring.common.SpringConfigKeys;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.springframework.beans.factory.DisposableBean;

import lombok.SneakyThrows;

public class EventMeshClientTemplate implements MessageSendingOperations, DisposableBean {

    private static final String SPRING_CONFIG_FILE_NAME = "application.properties";

    private static final String EVENTMESH_IP = "eventmesh.ip";

    private static final String EVENTMESH_PORT = "eventmesh.port";

    @Override
    public void destroy() throws Exception {

    }

    @Override
    public void send(Object message, ProtocolType protocolType, Long timeout) {
        IProducer producer;
        switch (protocolType) {
            case HTTP:
                producer = new EventMeshHttpProducer(initHttpClientConfig());
                producer.publishWithTimeout(message, timeout);
                break;
            case GRPC:
                producer = new EventMeshGrpcProducer(initGrpcClientConfig());
                producer.publishWithTimeout(message, timeout);
                break;
            case TCP:
                producer = new EventMeshTcpClientAdapter(initTcpClientConfig());
                producer.publishWithTimeout(message, timeout);
                break;
            default:
                throw new EventMeshException("Illegal protocol type.");
        }
    }

    @Override
    public void send(Object message, ProtocolType protocolType) {
        IProducer producer;
        switch (protocolType) {
            case HTTP:
                producer = new EventMeshHttpProducer(initHttpClientConfig());
                producer.publish(message);
                break;
            case GRPC:
                producer = new EventMeshGrpcProducer(initGrpcClientConfig());
                producer.publish(message);
                break;
            case TCP:
                producer = new EventMeshTcpClientAdapter(initTcpClientConfig());
                producer.publish(message);
                break;
            default:
                throw new EventMeshException("Illegal protocol type.");
        }
    }

    @SneakyThrows
    protected static EventMeshHttpClientConfig initHttpClientConfig() {
        final Properties properties = readPropertiesFile(SPRING_CONFIG_FILE_NAME);
        final String eventMeshIp = properties.getProperty(SpringConfigKeys.EVENTMESH_IP);
        final String eventMeshPort = properties.getProperty(SpringConfigKeys.EVENTMESH_HTTP_PORT);
        final String env = properties.getProperty(SpringConfigKeys.EVENTMESH_ENV, Constants.UNKNOWN);
        final String idc = properties.getProperty(SpringConfigKeys.EVENTMESH_IDC, Constants.UNKNOWN);
        final String sys = properties.getProperty(SpringConfigKeys.EVENTMESH_SYS, Constants.UNKNOWN);
        String eventMeshIPPort = eventMeshIp + ":" + eventMeshPort;

        return EventMeshHttpClientConfig.builder()
            .liteEventMeshAddr(eventMeshIPPort)
            .env(env)
            .idc(idc)
            .sys(sys)
            .ip(IPUtils.getLocalAddress())
            .pid(String.valueOf(ThreadUtils.getPID()))
            .build();
    }

    @SneakyThrows
    protected static EventMeshTCPClientConfig initTcpClientConfig() {
        final Properties properties = readPropertiesFile(SPRING_CONFIG_FILE_NAME);
        final String eventMeshIp = properties.getProperty(SpringConfigKeys.EVENTMESH_IP);
        final String eventMeshPort = properties.getProperty(SpringConfigKeys.EVENTMESH_TCP_PORT);
        final String env = properties.getProperty(SpringConfigKeys.EVENTMESH_ENV, Constants.UNKNOWN);
        final String idc = properties.getProperty(SpringConfigKeys.EVENTMESH_IDC, Constants.UNKNOWN);
        final String sys = properties.getProperty(SpringConfigKeys.EVENTMESH_SYS, Constants.UNKNOWN);

        UserAgent userAgent = UserAgent.builder()
            .host(IPUtils.getLocalAddress())
            .env(env)
            .idc(idc)
            .subsystem(sys)
            .build();

        return EventMeshTCPClientConfig.builder()
            .host(eventMeshIp)
            .port(Integer.parseInt(eventMeshPort))
            .userAgent(userAgent)
            .build();
    }

    @SneakyThrows
    protected static EventMeshGrpcClientConfig initGrpcClientConfig() {
        final Properties properties = readPropertiesFile(SPRING_CONFIG_FILE_NAME);
        final String eventMeshIp = properties.getProperty(SpringConfigKeys.EVENTMESH_IP);
        final String eventMeshPort = properties.getProperty(SpringConfigKeys.EVENTMESH_GRPC_PORT);
        final String env = properties.getProperty(SpringConfigKeys.EVENTMESH_ENV, Constants.UNKNOWN);
        final String idc = properties.getProperty(SpringConfigKeys.EVENTMESH_IDC, Constants.UNKNOWN);
        final String sys = properties.getProperty(SpringConfigKeys.EVENTMESH_SYS, Constants.UNKNOWN);

        return EventMeshGrpcClientConfig.builder()
            .serverAddr(eventMeshIp)
            .serverPort(Integer.parseInt(eventMeshPort))
            .env(env)
            .idc(idc)
            .sys(sys)
            .build();
    }

    /**
     * @param fileName
     * @return Properties
     */
    public static Properties readPropertiesFile(final String fileName) throws IOException {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(fileName)) {
            final Properties properties = new Properties();
            properties.load(inputStream);
            return properties;
        }
    }
}
