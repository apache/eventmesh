package org.apache.eventmesh.runtime.core.protocol.grpc.consumer;

import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription.SubscriptionItem.SubscriptionMode;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.common.ServiceState;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.ConsumerGroupClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
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

    public void registerClient(ConsumerGroupClient newClient) {
        String consumerGroup = newClient.getConsumerGroup();
        String topic = newClient.getTopic();
        String url = newClient.getUrl();
        SubscriptionMode subscriptionMode = newClient.getSubscriptionMode();
        List<ConsumerGroupClient> localClients = clientTable.get(consumerGroup);

        if (localClients == null) {
            localClients = new ArrayList<>();
            localClients.add(newClient);
            clientTable.put(consumerGroup, localClients);
        } else {
            boolean isContains = false;
            for (ConsumerGroupClient localClient : localClients) {
                if (StringUtils.equals(localClient.getTopic(), topic) && StringUtils.equals(localClient.getUrl(), url)
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

    public void restartEventMeshConsumer(String consumerGroup) throws Exception {
        EventMeshConsumer eventMeshConsumer = consumerTable.get(consumerGroup);

        if (eventMeshConsumer == null) {
            eventMeshConsumer = new EventMeshConsumer(eventMeshGrpcServer, consumerGroup);
            consumerTable.put(consumerGroup, eventMeshConsumer);
            eventMeshConsumer.init();
        }

        Set<String> oldTopicConfigs = eventMeshConsumer.buildTopicConfig();
        List<ConsumerGroupClient> localClients = clientTable.get(consumerGroup);
        for (ConsumerGroupClient client : localClients) {
            String topic = client.getTopic();
            String idc = client.getIdc();
            String url = client.getUrl();
            SubscriptionMode subscriptionMode = client.getSubscriptionMode();
            eventMeshConsumer.addTopicConfig(topic, subscriptionMode, idc, url);
        }

        // start up eventMeshConsumer the first time
        if (ServiceState.INITED.equals(eventMeshConsumer.getStatus())) {
            eventMeshConsumer.start();
            return;
        }

        // determine if restart eventMeshConsumer required
        Set<String> newTopicConfigs = eventMeshConsumer.buildTopicConfig();
        if (!oldTopicConfigs.equals(newTopicConfigs)) {
            if (ServiceState.RUNNING.equals(eventMeshConsumer.getStatus())) {
                eventMeshConsumer.shutdown();
            }
            eventMeshConsumer.start();
        }
    }
}