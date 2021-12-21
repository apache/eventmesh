package org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup;

import io.grpc.stub.StreamObserver;
import org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription.SubscriptionItem.SubscriptionMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class ConsumerGroupTopicConfig {
    public static Logger logger = LoggerFactory.getLogger(ConsumerGroupTopicConfig.class);

    private final String consumerGroup;

    private final String topic;

    private final SubscriptionMode subscriptionMode;

    private final String protocolDesc;

    /**
     * PUSH URL
     * <p>
     * Key: IDC
     * Value: list of URls
     */
    private final Map<String, List<String>> idcUrls = new ConcurrentHashMap<>();

    /**
     * Event streaming emitter
     * <br/>
     * Key: IDC
     * Value: list of emitters
     */
    private final Map<String, Map<String, StreamObserver<EventMeshMessage>>> idcEmitters = new ConcurrentHashMap<>();

    public ConsumerGroupTopicConfig(String consumerGroup, String topic, SubscriptionMode subscriptionMode, String protocolDesc) {
        this.consumerGroup = consumerGroup;
        this.topic = topic;
        this.subscriptionMode = subscriptionMode;
        this.protocolDesc = protocolDesc;
    }

    public synchronized void addUrl(String idc, String url) {
        List<String> urls = idcUrls.get(idc);
        if (urls == null) {
            urls = new LinkedList<>();
            urls.add(url);
            idcUrls.put(idc, urls);
        } else if (!urls.contains(url)) {
            urls.add(url);
        }
    }

    public synchronized void addEmitter(String idc, String clientIp, String clientPid, StreamObserver<EventMeshMessage> emitter) {
        Map<String, StreamObserver<EventMeshMessage>> emitters = idcEmitters.get(idc);
        if (emitters == null) {
            emitters = new HashMap<>();
            emitters.put(clientIp + clientPid, emitter);
            idcEmitters.put(idc, emitters);
        } else if (!emitters.containsKey(clientIp + clientPid)) {
            emitters.put(clientIp + clientPid, emitter);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ConsumerGroupTopicConfig that = (ConsumerGroupTopicConfig) o;
        return consumerGroup.equals(that.consumerGroup)
            && Objects.equals(topic, that.topic)
            && Objects.equals(idcUrls, that.idcUrls);
    }

    @Override
    public int hashCode() {
        return Objects.hash(consumerGroup, topic, idcUrls);
    }

    @Override
    public String toString() {
        return "consumeTopicConfig={consumerGroup=" + consumerGroup
            + ",topic=" + topic
            + ",idcUrls=" + idcUrls + "}";
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public String getTopic() {
        return topic;
    }

    public SubscriptionMode getSubscriptionMode() {
        return subscriptionMode;
    }

    public String getProtocolDesc() {
        return protocolDesc;
    }

    public Map<String, List<String>> getIdcUrls() {
        return idcUrls;
    }

    public Map<String, Map<String, StreamObserver<EventMeshMessage>>> getIdcEmitters() {
        return idcEmitters;
    }
}