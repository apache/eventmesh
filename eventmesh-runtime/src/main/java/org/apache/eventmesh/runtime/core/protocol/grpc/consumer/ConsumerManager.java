package org.apache.eventmesh.runtime.core.protocol.grpc.consumer;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription.SubscriptionItem.SubscriptionMode;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.common.ServiceState;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.ConsumerGroupClient;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.GrpcType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Event;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ConsumerManager {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final EventMeshGrpcServer eventMeshGrpcServer;

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
        logger.info("Grpc ConsumerManager started......");
    }

    public void shutdown() throws Exception {
        for (EventMeshConsumer consumer : consumerTable.values()) {
            consumer.shutdown();
        }
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
                    localClient.setLastUpTime(newClient.getLastUpTime());
                    break;
                } else if (GrpcType.STREAM.equals(grpcType) && StringUtils.equals(localClient.getTopic(), topic)
                    && StringUtils.equals(localClient.getIp(), ip) && StringUtils.equals(localClient.getPid(), pid)
                    && localClient.getSubscriptionMode().equals(subscriptionMode)) {
                    isContains = true;
                    localClient.setLastUpTime(newClient.getLastUpTime());
                    break;
                }
            }
            if (!isContains) {
                localClients.add(newClient);
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
            client.getEventEmitter().onCompleted();
        }
    }

    public synchronized void restartEventMeshConsumer(String consumerGroup) throws Exception {
        EventMeshConsumer eventMeshConsumer = consumerTable.get(consumerGroup);

        if (eventMeshConsumer == null) {
            return;
        }

        if (eventMeshConsumer.getStatus() == null) {
            eventMeshConsumer.init();
        } else if (ServiceState.RUNNING.equals(eventMeshConsumer.getStatus())) {
            eventMeshConsumer.shutdown();
        }
        eventMeshConsumer.start();
    }
}