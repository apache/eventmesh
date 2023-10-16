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

package org.apache.eventmesh.connector.spring.config;

import org.apache.eventmesh.connector.spring.common.SpringApplicationContextHolder;
import org.apache.eventmesh.connector.spring.server.SpringConnectServer;
import org.apache.eventmesh.connector.spring.sink.EventMeshMessageListenerBeanPostProcessor;
import org.apache.eventmesh.connector.spring.sink.connector.SpringSinkConnector;
import org.apache.eventmesh.connector.spring.source.connector.SpringSourceConnector;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring auto configuration.
 */
@Configuration
public class EventMeshAutoConfiguration {

    public static final String SPRING_SOURCE_CONNECTOR_BEAN_NAME = "springSourceConnector";
    public static final String SPRING_SINK_CONNECTOR_BEAN_NAME = "springSinkConnector";
    public static final String SPRING_CONNECT_SERVER_BEAN_NAME = "springConnectServer";
    public static final String SPRING_APPLICATION_CONTEXT_HOLDER = "springApplicationContextHolder";
    public static final String EVENTMESH_MESSAGE_LISTENER_BEAN_POST_PROCESSOR = "eventMeshMessageListenerBeanPostProcessor";

    @Bean(name = SPRING_SOURCE_CONNECTOR_BEAN_NAME)
    @ConditionalOnMissingBean(name = SPRING_SOURCE_CONNECTOR_BEAN_NAME)
    public SpringSourceConnector springSourceConnector() {
        return new SpringSourceConnector();
    }

    @Bean(name = SPRING_SINK_CONNECTOR_BEAN_NAME)
    @ConditionalOnMissingBean(name = SPRING_SINK_CONNECTOR_BEAN_NAME)
    public SpringSinkConnector springSinkConnector() {
        return new SpringSinkConnector();
    }

    @Bean(name = SPRING_CONNECT_SERVER_BEAN_NAME)
    @ConditionalOnMissingBean(name = SPRING_CONNECT_SERVER_BEAN_NAME)
    public SpringConnectServer springConnectServer() {
        return new SpringConnectServer();
    }

    @Bean(name = SPRING_APPLICATION_CONTEXT_HOLDER)
    @ConditionalOnMissingBean(name = SPRING_APPLICATION_CONTEXT_HOLDER)
    public SpringApplicationContextHolder springApplicationContextHolder() {
        return new SpringApplicationContextHolder();
    }

    @Bean(name = EVENTMESH_MESSAGE_LISTENER_BEAN_POST_PROCESSOR)
    @ConditionalOnMissingBean(name = EVENTMESH_MESSAGE_LISTENER_BEAN_POST_PROCESSOR)
    public EventMeshMessageListenerBeanPostProcessor eventMeshMessageListenerBeanPostProcessor() {
        return new EventMeshMessageListenerBeanPostProcessor();
    }
}
