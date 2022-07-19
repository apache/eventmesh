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

package org.apache.eventmesh.client.common;

import org.apache.eventmesh.client.common.resolver.EventMeshNameResolver;
import org.apache.eventmesh.client.common.strategy.ClientLoadBalanceStrategy;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;


public abstract class AbstractLoadBalance<T extends LoadBalanceClient, C extends ClientConfig> implements LoadBalanceAbility<T> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractLoadBalance.class);

    private final Map<String, T> producerMap = Maps.newConcurrentMap();

    private final C clientConfig;

    private EventMeshNameResolver nameResolver;

    private ClientLoadBalanceStrategy<T> loadBalanceStrategy;

    public AbstractLoadBalance(C config) {

        Optional.ofNullable(config.getRegisterUri()).orElseThrow(() -> new IllegalArgumentException("registerUri must not be null"));

        this.clientConfig = config;

        String nameResolverName = StringUtils.defaultIfEmpty(this.clientConfig.getNameResolverName(), "default");
        this.nameResolver = this.loadNameResolver(nameResolverName);

        String loadBalanceStrategyName = StringUtils.defaultIfBlank(this.clientConfig.getLoadBalanceStrategyName(), "random");
        this.loadBalanceStrategy = this.loadBalanceStrategy(loadBalanceStrategyName);
    }


    @Override
    public List<T> initClient() {

        this.nameResolver.start(serverList -> this.refreshServer(serverList));

        return this.getClients();
    }

    private EventMeshNameResolver loadNameResolver(String nameResolverName) {
        final EventMeshNameResolver nameResolver =
            EventMeshExtensionFactory.getExtension(EventMeshNameResolver.class, nameResolverName);

        if (Objects.isNull(nameResolver)) {
            throw new IllegalArgumentException(nameResolverName + " nameResolver is not exist");
        }

        return nameResolver;
    }

    @SuppressWarnings("unchecked")
    private ClientLoadBalanceStrategy<T> loadBalanceStrategy(String strategyName) {
        final ClientLoadBalanceStrategy<T> loadBalanceStrategy =
            EventMeshExtensionFactory.getExtension(ClientLoadBalanceStrategy.class, strategyName);

        if (Objects.isNull(loadBalanceStrategy)) {
            throw new IllegalArgumentException(strategyName + " ClientLoadBalanceStrategy is not exist");
        }

        return loadBalanceStrategy;
    }

    private synchronized void refreshServer(Set<EventMeshAddress> addressSet) {

        final Set<String> currentServerList = Optional.ofNullable(addressSet)
            .orElse(Sets.newConcurrentHashSet())
            .stream()
            .map(p -> p.getHost() + ":" + p.getPort())
            .collect(Collectors.toSet());

        final Set<String> oldServerList = this.producerMap.keySet();

        final Sets.SetView<String> expireServerView = Sets.difference(oldServerList, currentServerList);
        if (!expireServerView.isEmpty()) {
            while (expireServerView.iterator().hasNext()) {
                final String expireServer = expireServerView.iterator().next();
                logger.info("[{}.refreshServer] server of {} is expired", this.getClass().getSimpleName(), expireServer);

                this.producerMap.remove(expireServer);
            }
        }

        final Sets.SetView<String> addServerView = Sets.difference(currentServerList, oldServerList);
        if (!addServerView.isEmpty()) {
            while (addServerView.iterator().hasNext()) {
                final String addServer = addServerView.iterator().next();
                logger.info("[{}.refreshServer] server of {} is added", this.getClass().getSimpleName(), addServer);

                final String[] ipPort = StringUtils.split(addServer, ":");
                final EventMeshAddress address = new EventMeshAddress();
                address.setHost(ipPort[0]);
                address.setPort(Integer.parseInt(ipPort[1]));

                final T client = this.createClient(this.clientConfig, address);
                this.producerMap.put(addServer, client);
            }
        }
    }


    @Override
    public T select() {
        final T client = this.loadBalanceStrategy.select(this.getClients());
        return client;
    }

    @Override
    public void close() throws Exception {

        this.nameResolver.shutdown();

        for (T t : this.getClients()) {
            t.shutdown();
        }
    }

    private List<T> getClients() {
        return Lists.newArrayList(this.producerMap.values());
    }

    protected abstract T createClient(C config, EventMeshAddress address);
}
