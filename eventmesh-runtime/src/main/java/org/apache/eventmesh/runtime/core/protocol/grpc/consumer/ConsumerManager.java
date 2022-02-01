package org.apache.eventmesh.runtime.core.protocol.grpc.consumer;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription.SubscriptionItem.SubscriptionMode;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.common.ServiceState;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.ConsumerGroupClient;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.GrpcType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ConsumerManager {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final EventMeshGrpcServer eventMeshGrpcServer;

    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    // key: ConsumerGroup
    private final Map<String, List<ConsumerGroupClient>> clientTable = new ConcurrentHashMap<>();

    // key: ConsumerGroup
    private final Map<String, EventMeshConsumer> consumerTable = new ConcurrentHashMap<>();

    public ConsumerManager(EventMeshGrpcServer eventMeshGrpcServer) {
        this.eventMeshGrpcServer = eventMeshGrpcServer;
    }

    public void init() throws Exception {
        logger.info("Grpc ConsumerManager initialized......");
    }

    public void start() throws Exception {
        startClientCheck();
        logger.info("Grpc ConsumerManager started......");
    }

    public void shutdown() throws Exception {
        for (EventMeshConsumer consumer : consumerTable.values()) {
            consumer.shutdown();
        }
        scheduledExecutorService.shutdown();
        logger.info("Grpc ConsumerManager shutdown......");
    }

    public EventMeshConsumer getEventMeshConsumer(String consumerGroup) {
        EventMeshConsumer consumer = consumerTable.get(consumerGroup);
        if (consumer == null) {
            consumer = new EventMeshConsumer(eventMeshGrpcServer, consumerGroup);
            consumerTable.put(consumerGroup, consumer);
        }
        return consumer;
    }

    public synchronized void registerClient(ConsumerGroupClient newClient) {
        String consumerGroup = newClient.getConsumerGroup();
        String topic = newClient.getTopic();
        GrpcType grpcType = newClient.getGrpcType();
        String url = newClient.getUrl();
        String ip = newClient.getIp();
        String pid = newClient.getPid();
        SubscriptionMode subscriptionMode = newClient.getSubscriptionMode();
        List<ConsumerGroupClient> localClients = clientTable.get(consumerGroup);

        if (localClients == null) {
            localClients = new ArrayList<>();
            localClients.add(newClient);
            clientTable.put(consumerGroup, localClients);
        } else {
            boolean isContains = false;
            for (ConsumerGroupClient localClient : localClients) {
                if (GrpcType.WEBHOOK.equals(grpcType) && StringUtils.equals(localClient.getTopic(), topic)
                    && StringUtils.equals(localClient.getUrl(), url)
                    && localClient.getSubscriptionMode().equals(subscriptionMode)) {
                    isContains = true;
                    localClient.setUrl(newClient.getUrl());
                    localClient.setLastUpTime(newClient.getLastUpTime());
                    break;
                } else if (GrpcType.STREAM.equals(grpcType) && StringUtils.equals(localClient.getTopic(), topic)
                    && StringUtils.equals(localClient.getIp(), ip) && StringUtils.equals(localClient.getPid(), pid)
                    && localClient.getSubscriptionMode().equals(subscriptionMode)) {
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

    public void updateClientTime(ConsumerGroupClient client) {
        String consumerGroup = client.getConsumerGroup();
        List<ConsumerGroupClient> localClients = clientTable.get(consumerGroup);
        if (CollectionUtils.isEmpty(localClients)) {
            return;
        }
        for (ConsumerGroupClient localClient : localClients) {
            if (StringUtils.equals(localClient.getIp(), client.getIp())
                && StringUtils.equals(localClient.getPid(), client.getPid())
                && StringUtils.equals(localClient.getSys(), client.getSys())
                && StringUtils.equals(localClient.getTopic(), client.getTopic())) {
                localClient.setLastUpTime(new Date());
                break;
            }
        }
    }

    public synchronized void deregisterClient(ConsumerGroupClient client) {
        String consumerGroup = client.getConsumerGroup();
        List<ConsumerGroupClient> localClients = clientTable.get(consumerGroup);
        if (CollectionUtils.isEmpty(localClients)) {
            return;
        }

        Iterator<ConsumerGroupClient> iterator = localClients.iterator();
        while (iterator.hasNext()) {
            ConsumerGroupClient localClient = iterator.next();
            if (StringUtils.equals(localClient.getTopic(), client.getTopic())
                && localClient.getSubscriptionMode().equals(client.getSubscriptionMode())) {

                // close the GRPC client stream before removing it
                closeEventStream(localClient);
                iterator.remove();
            }
        }

        if (localClients.size() == 0) {
            clientTable.remove(consumerGroup);
        }
    }

    private void closeEventStream(ConsumerGroupClient client) {
        if (client.getEventEmitter() != null) {
            try {
                client.getEventEmitter().onCompleted();
            } catch (Exception e) {
                logger.warn("GRPC client {} already closed.", client.toString());
            }
        }
    }

    public synchronized void restartEventMeshConsumer(String consumerGroup) throws Exception {
        EventMeshConsumer eventMeshConsumer = consumerTable.get(consumerGroup);

        if (eventMeshConsumer == null) {
            return;
        }

        if (ServiceState.RUNNING.equals(eventMeshConsumer.getStatus())) {
            eventMeshConsumer.shutdown();
        }

        eventMeshConsumer.init();
        eventMeshConsumer.start();

        if (!ServiceState.RUNNING.equals(eventMeshConsumer.getStatus())) {
            consumerTable.remove(consumerGroup);
        }
    }

    private void startClientCheck() {
        int clientTimeout = eventMeshGrpcServer.getEventMeshGrpcConfiguration().eventMeshSessionExpiredInMills;
        if (clientTimeout > 0) {
            scheduledExecutorService.scheduleAtFixedRate(() -> {
                logger.info("grpc client info check");
                List<ConsumerGroupClient> clientList = new LinkedList<>();
                for (List<ConsumerGroupClient> clients : clientTable.values()) {
                    clientList.addAll(clients);
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("total number of ConsumerGroupClients: {}", clientList.size());
                }

                if (clientList.isEmpty()) {
                    return;
                }
                Set<String> consumerGroupRestart = new HashSet<>();
                for (ConsumerGroupClient client : clientList) {
                    if (System.currentTimeMillis() - client.getLastUpTime().getTime() > clientTimeout) {
                        logger.warn("client {} lastUpdate time {} over three heartbeat cycles. Removing it",
                            JsonUtils.serialize(client), client.getLastUpTime());
                        String consumerGroup = client.getConsumerGroup();
                        EventMeshConsumer consumer = getEventMeshConsumer(consumerGroup);

                        deregisterClient(client);
                        if (consumer.deregisterClient(client)) {
                            consumerGroupRestart.add(consumerGroup);
                        }
                    }
                }

                // restart EventMeshConsumer for the group
                for (String consumerGroup : consumerGroupRestart) {
                    try {
                        restartEventMeshConsumer(consumerGroup);
                    } catch (Exception e) {
                        logger.error("Error in restarting EventMeshConsumer [{}]", consumerGroup, e);
                    }
                }
            }, 10000, 10000, TimeUnit.MILLISECONDS);
        }
    }
}