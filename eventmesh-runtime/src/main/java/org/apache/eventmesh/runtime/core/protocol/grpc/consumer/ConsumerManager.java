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

package org.apache.eventmesh.runtime.core.protocol.grpc.consumer;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.common.utils.LogUtils;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.common.ServiceState;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.ConsumerGroupClient;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.GrpcType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class ConsumerManager {

    private final EventMeshGrpcServer eventMeshGrpcServer;

    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    // key: ConsumerGroup
    private final Map<String, List<ConsumerGroupClient>> clientTable = new ConcurrentHashMap<>();

    // key: ConsumerGroup
    private final Map<String, EventMeshConsumer> consumerTable = new ConcurrentHashMap<>();

    public ConsumerManager(final EventMeshGrpcServer eventMeshGrpcServer) {
        this.eventMeshGrpcServer = eventMeshGrpcServer;
    }

    public Map<String, List<ConsumerGroupClient>> getClientTable() {
        return clientTable;
    }

    public void init() throws Exception {
        LogUtils.info(log, "Grpc ConsumerManager initialized.");
    }

    public void start() throws Exception {
        startClientCheck();
        LogUtils.info(log, "Grpc ConsumerManager started.");
    }

    public void shutdown() throws Exception {
        for (final EventMeshConsumer consumer : consumerTable.values()) {
            consumer.shutdown();
        }
        scheduledExecutorService.shutdown();
        LogUtils.info(log, "Grpc ConsumerManager shutdown.");
    }

    public EventMeshConsumer getEventMeshConsumer(final String consumerGroup) {
        return consumerTable.computeIfAbsent(consumerGroup, key -> new EventMeshConsumer(eventMeshGrpcServer, consumerGroup));
    }

    public synchronized void registerClient(final ConsumerGroupClient newClient) {
        final String consumerGroup = newClient.getConsumerGroup();
        final String topic = newClient.getTopic();
        final GrpcType grpcType = newClient.getGrpcType();
        final String url = newClient.getUrl();
        final String ip = newClient.getIp();
        final String pid = newClient.getPid();
        final SubscriptionMode subscriptionMode = newClient.getSubscriptionMode();

        List<ConsumerGroupClient> localClients = clientTable.get(consumerGroup);
        if (localClients == null) {
            localClients = new ArrayList<>();
            localClients.add(newClient);
            clientTable.putIfAbsent(consumerGroup, localClients);
        } else {
            boolean isContains = false;
            for (final ConsumerGroupClient localClient : localClients) {
                if (GrpcType.WEBHOOK == grpcType && StringUtils.equals(localClient.getTopic(), topic)
                        && StringUtils.equals(localClient.getUrl(), url)
                        && localClient.getSubscriptionMode() == subscriptionMode) {
                    isContains = true;
                    localClient.setUrl(newClient.getUrl());
                    localClient.setLastUpTime(newClient.getLastUpTime());
                    break;
                } else if (GrpcType.STREAM == grpcType && StringUtils.equals(localClient.getTopic(), topic)
                        && StringUtils.equals(localClient.getIp(), ip) && StringUtils.equals(localClient.getPid(), pid)
                        && localClient.getSubscriptionMode() == subscriptionMode) {
                    isContains = true;
                    localClient.setEventEmitter(newClient.getEventEmitter());
                    localClient.setLastUpTime(newClient.getLastUpTime());
                    break;
                }
            }
            if (!isContains) {
                localClients.add(newClient);
            }
        }
    }

    public boolean updateClientTime(final ConsumerGroupClient client) {
        final List<ConsumerGroupClient> localClients = clientTable.get(client.getConsumerGroup());
        if (CollectionUtils.isEmpty(localClients)) {
            return false;
        }

        for (final ConsumerGroupClient localClient : localClients) {
            if (StringUtils.equals(localClient.getIp(), client.getIp())
                    && StringUtils.equals(localClient.getPid(), client.getPid())
                    && StringUtils.equals(localClient.getSys(), client.getSys())
                    && StringUtils.equals(localClient.getTopic(), client.getTopic())) {
                localClient.setLastUpTime(new Date());
                return true;
            }
        }

        return false;
    }

    public synchronized void deregisterClient(final ConsumerGroupClient client) {
        final String consumerGroup = client.getConsumerGroup();
        final List<ConsumerGroupClient> localClients = clientTable.get(consumerGroup);
        if (CollectionUtils.isEmpty(localClients)) {
            return;
        }

        final Iterator<ConsumerGroupClient> iterator = localClients.iterator();
        synchronized (clientTable) {
            while (iterator.hasNext()) {
                final ConsumerGroupClient localClient = iterator.next();
                if (StringUtils.equals(localClient.getTopic(), client.getTopic())
                        && localClient.getSubscriptionMode() == client.getSubscriptionMode()) {
                    // close the GRPC client stream before removing it
                    closeEventStream(localClient);
                    iterator.remove();
                }
            }
        }

        if (CollectionUtils.isEmpty(localClients)) {
            clientTable.remove(consumerGroup);
        }

    }

    private void closeEventStream(final ConsumerGroupClient client) {
        if (client.getEventEmitter() != null) {
            client.getEventEmitter().onCompleted();
        }
    }

    public synchronized void restartEventMeshConsumer(final String consumerGroup) throws Exception {
        final EventMeshConsumer eventMeshConsumer = consumerTable.get(consumerGroup);

        if (eventMeshConsumer == null) {
            return;
        }

        if (ServiceState.RUNNING == eventMeshConsumer.getStatus()) {
            eventMeshConsumer.shutdown();
        }

        eventMeshConsumer.init();
        eventMeshConsumer.start();

        if (ServiceState.RUNNING != eventMeshConsumer.getStatus()) {
            consumerTable.remove(consumerGroup);
        }
    }

    private void startClientCheck() {
        final int clientTimeout = eventMeshGrpcServer.getEventMeshGrpcConfiguration().getEventMeshSessionExpiredInMills();
        if (clientTimeout > 0) {
            scheduledExecutorService.scheduleAtFixedRate(() -> {
                LogUtils.debug(log, "grpc client info check");

                final List<ConsumerGroupClient> clientList = new ArrayList<>();
                clientTable.values().forEach(clientList::addAll);

                LogUtils.debug(log, "total number of ConsumerGroupClients: {}", clientList.size());

                if (CollectionUtils.isEmpty(clientList)) {
                    return;
                }

                final Set<String> consumerGroupRestart = new HashSet<>();
                clientList.forEach(client -> {
                    if (System.currentTimeMillis() - client.getLastUpTime().getTime() > clientTimeout) {
                        LogUtils.warn(log, "client {} lastUpdate time {} over three heartbeat cycles. Removing it",
                                JsonUtils.toJSONString(client), client.getLastUpTime());

                        deregisterClient(client);
                        if (getEventMeshConsumer(client.getConsumerGroup()).deregisterClient(client)) {
                            consumerGroupRestart.add(client.getConsumerGroup());
                        }
                    }
                });

                // restart EventMeshConsumer for the group
                consumerGroupRestart.forEach(consumerGroup -> {
                    try {
                        restartEventMeshConsumer(consumerGroup);
                    } catch (Exception e) {
                        LogUtils.error(log, "Error in restarting EventMeshConsumer [{}]", consumerGroup, e);
                    }
                });
            }, 10_000, 10_000, TimeUnit.MILLISECONDS);
        }
    }

    public List<String> getAllConsumerTopic() {
        return clientTable.values()
                .stream()
                .flatMap(List::stream)
                .map(ConsumerGroupClient::getTopic)
                .distinct()
                .collect(Collectors.toList());
    }
}
