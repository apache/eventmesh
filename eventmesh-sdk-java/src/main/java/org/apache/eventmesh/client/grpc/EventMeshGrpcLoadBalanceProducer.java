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

package org.apache.eventmesh.client.grpc;


import org.apache.eventmesh.client.extension.EventMeshNameResolver;
import org.apache.eventmesh.client.extension.EventMeshNameResolverProvider;
import org.apache.eventmesh.client.extension.loadBalancer.EventMeshClientLoadBalancer;
import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.client.grpc.producer.EventMeshGrpcProducer;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class EventMeshGrpcLoadBalanceProducer {

    private static final Logger logger = LoggerFactory.getLogger(EventMeshGrpcLoadBalanceProducer.class);

    private EventMeshGrpcProducer defaultProducer;

    private Map<String, EventMeshGrpcProducer> producerMap = Maps.newConcurrentMap();

    private EventMeshGrpcClientConfigWrapper configWrapper;

    private EventMeshNameResolver nameResolver;

    private EventMeshClientLoadBalancer<EventMeshGrpcProducer> loadBalancer;


    public EventMeshGrpcLoadBalanceProducer(EventMeshGrpcClientConfigWrapper configWrapper) {
        this.configWrapper = configWrapper;
    }

    public void init() {
        final EventMeshGrpcClientConfig clientConfig = configWrapper.getClientConfig();

        nameResolver = Optional.ofNullable(configWrapper.getNameResolver())
            .orElseGet(() -> getDefaultNameResolver());

        if (StringUtils.isBlank(configWrapper.getUri()) || Objects.isNull(nameResolver)) {
            defaultProducer = createProducer(clientConfig);
            return;
        }

        Optional.ofNullable(nameResolver.resolver())
            .orElseGet(() -> Sets.newConcurrentHashSet())
            .stream()
            .forEach(p -> {
                final EventMeshGrpcClientConfig config = ObjectUtils.clone(clientConfig);
                final String[] ipPort = StringUtils.split(p, ":");
                config.setServerAddr(ipPort[0]);
                config.setServerPort(Integer.parseInt(ipPort[1]));
                createProducer(config);
            });

        this.loadBalancer = Optional.ofNullable(configWrapper.getEventMeshClientLoadBalancer())
            .orElseGet(() -> getDefaultLoadBalancer());
    }

    public Response publish(EventMeshMessage eventMeshMessage) {

        return selectProducer().publish(eventMeshMessage);
    }

    private EventMeshClientLoadBalancer getDefaultLoadBalancer() {
        final EventMeshClientLoadBalancer loadBalancer =
            EventMeshExtensionFactory.getExtension(EventMeshClientLoadBalancer.class, "clientLoadBalancer");

        //todo
        return Optional.ofNullable(loadBalancer).orElseGet(() -> new EventMeshClientLoadBalancer() {
            @Override
            public Object select(List serverList) {

                return null;
            }
        });

    }

    private EventMeshNameResolver getDefaultNameResolver() {
        final EventMeshNameResolverProvider resolverProvider =
            EventMeshExtensionFactory.getExtension(EventMeshNameResolverProvider.class, "nameResolverProvider");

        if (resolverProvider == null) {
            return null;
        }

        try {
            URI uri = new URI(configWrapper.getUri());
            return resolverProvider.newNameResolver(uri, this::refreshServer);
        } catch (URISyntaxException e) {
            logger.error("load extension NameResolver failed, error {}", e.getMessage());
            return null;
        }
    }


    private synchronized void refreshServer(Set<String> serverList) {
        final Set<String> oldServerList = producerMap.keySet();

        final Sets.SetView<String> expireServerView = Sets.difference(oldServerList, serverList);
        if (!expireServerView.isEmpty()) {
            while (expireServerView.iterator().hasNext()) {
                final String expireServer = expireServerView.iterator().next();
                producerMap.remove(expireServer);
            }
        }

        final Sets.SetView<String> addServerView = Sets.difference(serverList, oldServerList);
        if (!addServerView.isEmpty()) {
            while (addServerView.iterator().hasNext()) {
                final String addServer = addServerView.iterator().next();
                final EventMeshGrpcClientConfig clientConfig = configWrapper.getClientConfig();
                final String[] ipPort = StringUtils.split(addServer, ":");
                clientConfig.setServerAddr(ipPort[0]);
                clientConfig.setServerPort(Integer.parseInt(ipPort[1]));
                createProducer(clientConfig);
            }
        }
    }

    private EventMeshGrpcProducer createProducer(EventMeshGrpcClientConfig clientConfig) {
        final EventMeshGrpcProducer producer = new EventMeshGrpcProducer(clientConfig);
        final String serverAddr = clientConfig.getServerAddr() + ":" + clientConfig.getServerPort();
        producerMap.put(serverAddr, producer);
        return producer;
    }

    private EventMeshGrpcProducer selectProducer() {

        if (Objects.nonNull(defaultProducer)) {
            return defaultProducer;
        }

        final EventMeshGrpcProducer producer = loadBalancer.select(Lists.newArrayList(producerMap.values()));

        return producer;
    }

}
