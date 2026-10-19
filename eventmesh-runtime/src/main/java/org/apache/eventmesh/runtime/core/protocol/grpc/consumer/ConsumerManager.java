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

import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.common.GrpcType;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.common.ServiceState;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.ConsumerGroupClient;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.EventEmitter;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConsumerManager {

    private final EventMeshGrpcServer eventMeshGrpcServer;

    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    // Independent of the bounded gRPC push retry timer, so timer saturation cannot stop broker replay recovery.
    private final ScheduledExecutorService recoveryExecutor;

    private final Map<String, RecoveryState> recoveringGroups = new ConcurrentHashMap<>();

    private volatile boolean shuttingDown;

    // key: ConsumerGroup
    private final Map<String, List<ConsumerGroupClient>> clientTable = new ConcurrentHashMap<>();

    // key: ConsumerGroup
    private final Map<String, EventMeshConsumer> consumerTable = new ConcurrentHashMap<>();

    public ConsumerManager(final EventMeshGrpcServer eventMeshGrpcServer) {
        this(eventMeshGrpcServer, Executors.newSingleThreadScheduledExecutor());
    }

    ConsumerManager(final EventMeshGrpcServer eventMeshGrpcServer, final ScheduledExecutorService recoveryExecutor) {
        this.eventMeshGrpcServer = eventMeshGrpcServer;
        this.recoveryExecutor = recoveryExecutor;
    }

    public Map<String, List<ConsumerGroupClient>> getClientTable() {
        return clientTable;
    }

    public void init() throws Exception {
        log.info("Grpc ConsumerManager initialized.");
    }

    public void start() throws Exception {
        startClientCheck();
        log.info("Grpc ConsumerManager started.");
    }

    public synchronized void shutdown() throws Exception {
        shuttingDown = true;
        recoveryExecutor.shutdownNow();
        recoveringGroups.clear();
        for (final EventMeshConsumer consumer : consumerTable.values()) {
            consumer.shutdown();
        }
        scheduledExecutorService.shutdown();
        log.info("Grpc ConsumerManager shutdown.");
    }

    public EventMeshConsumer getEventMeshConsumer(final String consumerGroup) {
        return consumerTable.computeIfAbsent(consumerGroup, key -> new EventMeshConsumer(eventMeshGrpcServer, consumerGroup));
    }

    public synchronized boolean registerStreamClients(final List<ConsumerGroupClient> clients, final EventEmitter<CloudEvent> emitter)
        throws Exception {
        if (shuttingDown || emitter.isClosed()) {
            return false;
        }
        if (clients.isEmpty()) {
            return true;
        }

        String consumerGroup = clients.get(0).getConsumerGroup();
        EventMeshConsumer consumer = getEventMeshConsumer(consumerGroup);
        boolean requireRestart = false;
        for (ConsumerGroupClient client : clients) {
            registerClient(client);
            if (consumer.registerClient(client)) {
                requireRestart = true;
            }
        }

        if (emitter.isClosed()) {
            deregisterClientsByEmitter(emitter);
            return false;
        }
        if (requireRestart) {
            restartEventMeshConsumer(consumerGroup);
        }
        return true;
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

    public synchronized boolean updateClientTime(final ConsumerGroupClient client) {
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
                if (matchesClient(localClient, client)) {
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

    private boolean matchesClient(ConsumerGroupClient localClient, ConsumerGroupClient client) {
        if (localClient == client) {
            return true;
        }
        if (!StringUtils.equals(localClient.getTopic(), client.getTopic())
            || localClient.getSubscriptionMode() != client.getSubscriptionMode()
            || (client.getGrpcType() != null && localClient.getGrpcType() != client.getGrpcType())) {
            return false;
        }
        if (localClient.getGrpcType() == GrpcType.STREAM) {
            return client.getIp() != null && client.getPid() != null
                && StringUtils.equals(localClient.getIp(), client.getIp())
                && StringUtils.equals(localClient.getPid(), client.getPid());
        }
        return localClient.getGrpcType() == GrpcType.WEBHOOK && client.getUrl() != null
            && StringUtils.equals(localClient.getUrl(), client.getUrl());
    }

    public synchronized void deregisterClientsByEmitter(final EventEmitter<CloudEvent> emitter) {
        final Set<String> groupsToRestart = new HashSet<>();
        clientTable.forEach((consumerGroup, clients) -> {
            final Iterator<ConsumerGroupClient> iterator = clients.iterator();
            while (iterator.hasNext()) {
                ConsumerGroupClient client = iterator.next();
                if (client.getGrpcType() == GrpcType.STREAM && client.getEventEmitter() == emitter) {
                    iterator.remove();
                    EventMeshConsumer consumer = consumerTable.get(consumerGroup);
                    if (consumer != null && consumer.deregisterClient(client)) {
                        groupsToRestart.add(consumerGroup);
                    }
                }
            }
            if (clients.isEmpty()) {
                clientTable.remove(consumerGroup, clients);
            }
        });
        groupsToRestart.forEach(this::restartEventMeshConsumerWithRecovery);
    }

    private void closeEventStream(final ConsumerGroupClient client) {
        if (client.getEventEmitter() != null) {
            client.getEventEmitter().onCompleted();
        }
    }

    public synchronized void restartEventMeshConsumer(final String consumerGroup) throws Exception {
        if (shuttingDown) {
            return;
        }
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

    public boolean recoverUnacknowledgedMessages(final String consumerGroup) {
        while (!shuttingDown) {
            RecoveryState state = recoveringGroups.computeIfAbsent(consumerGroup, group -> new RecoveryState());
            synchronized (state) {
                if (shuttingDown) {
                    return false;
                }
                if (recoveringGroups.get(consumerGroup) != state) {
                    continue;
                }
                if (state.scheduled) {
                    // A request arriving during recovery needs another restart after the current attempt succeeds.
                    state.repeat = true;
                    return true;
                }
                state.scheduled = true;
                if (!scheduleRecovery(consumerGroup, state, 0)) {
                    state.scheduled = false;
                    recoveringGroups.remove(consumerGroup, state);
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    void restartEventMeshConsumerWithRecovery(String consumerGroup) {
        try {
            restartEventMeshConsumer(consumerGroup);
        } catch (Exception e) {
            log.error("Error restarting EventMeshConsumer [{}] after client removal", consumerGroup, e);
            recoverUnacknowledgedMessages(consumerGroup);
        }
    }

    private boolean scheduleRecovery(final String consumerGroup, final RecoveryState state, long delayMillis) {
        try {
            recoveryExecutor.schedule(() -> {
                if (shuttingDown) {
                    synchronized (state) {
                        state.scheduled = false;
                        recoveringGroups.remove(consumerGroup, state);
                    }
                    return;
                }
                try {
                    restartEventMeshConsumer(consumerGroup);
                } catch (Exception e) {
                    log.error("Could not restart EventMeshConsumer [{}] to recover unacknowledged messages; retrying", consumerGroup, e);
                    if (!scheduleRecovery(consumerGroup, state, EventMeshConstants.DEFAULT_PUSH_RETRY_TIME_DISTANCE_IN_MILLSECONDS)) {
                        synchronized (state) {
                            state.scheduled = false;
                            recoveringGroups.remove(consumerGroup, state);
                        }
                    }
                    return;
                }
                synchronized (state) {
                    if (state.repeat) {
                        state.repeat = false;
                        if (!scheduleRecovery(consumerGroup, state, 0)) {
                            state.scheduled = false;
                            recoveringGroups.remove(consumerGroup, state);
                        }
                    } else {
                        state.scheduled = false;
                        recoveringGroups.remove(consumerGroup, state);
                    }
                }
            }, delayMillis, TimeUnit.MILLISECONDS);
            return true;
        } catch (RejectedExecutionException e) {
            log.error("Could not schedule recovery for EventMeshConsumer [{}]", consumerGroup, e);
            return false;
        }
    }

    private static final class RecoveryState {

        private boolean scheduled;

        private boolean repeat;
    }

    private void startClientCheck() {
        final int clientTimeout = eventMeshGrpcServer.getEventMeshGrpcConfiguration().getEventMeshSessionExpiredInMills();
        if (clientTimeout > 0) {
            scheduledExecutorService.scheduleAtFixedRate(() -> {
                log.debug("grpc client info check");

                final List<ConsumerGroupClient> clientList = new ArrayList<>();
                clientTable.values().forEach(clientList::addAll);

                log.debug("total number of ConsumerGroupClients: {}", clientList.size());

                if (CollectionUtils.isEmpty(clientList)) {
                    return;
                }

                final Set<String> consumerGroupRestart = new HashSet<>();
                clientList.forEach(client -> {
                    if (deregisterExpiredClient(client, clientTimeout)) {
                        consumerGroupRestart.add(client.getConsumerGroup());
                    }
                });

                // restart EventMeshConsumer for the group
                consumerGroupRestart.forEach(this::restartEventMeshConsumerWithRecovery);
            }, 10_000, 10_000, TimeUnit.MILLISECONDS);
        }
    }

    boolean deregisterExpiredClient(ConsumerGroupClient client, int clientTimeout) {
        synchronized (this) {
            List<ConsumerGroupClient> clients = clientTable.get(client.getConsumerGroup());
            if (CollectionUtils.isEmpty(clients) || !clients.contains(client)
                || System.currentTimeMillis() - client.getLastUpTime().getTime() <= clientTimeout) {
                return false;
            }
            log.warn("client {} lastUpdate time {} over three heartbeat cycles. Removing it",
                JsonUtils.toJSONString(client), client.getLastUpTime());
            deregisterClient(client);
        }
        return getEventMeshConsumer(client.getConsumerGroup()).deregisterClient(client);
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
