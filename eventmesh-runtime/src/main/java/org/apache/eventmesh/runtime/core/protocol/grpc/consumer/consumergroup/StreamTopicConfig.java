package org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup;

import io.grpc.stub.StreamObserver;
import org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription.SubscriptionItem.SubscriptionMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class StreamTopicConfig extends ConsumerGroupTopicConfig {
    private final Logger logger = LoggerFactory.getLogger(StreamTopicConfig.class);

    /**
     * Event streaming emitter
     * <br/>
     * Key: IDC
     * Value: list of emitters
     */
    private final Map<String, Map<String, StreamObserver<EventMeshMessage>>> idcEmitters = new ConcurrentHashMap<>();

    public StreamTopicConfig(String consumerGroup, String topic, SubscriptionMode subscriptionMode) {
        super(consumerGroup, topic, subscriptionMode, GrpcType.STREAM);
    }

    @Override
    public synchronized void registerClient(ConsumerGroupClient client) {
        if (!client.getGrpcType().equals(grpcType)) {
            logger.warn("Invalid grpc type: {}, expecting grpc type: {}, can not register client {}",
                client.getGrpcType(), grpcType, client.toString());
            return;
        }
        String idc = client.getIdc();
        String clientIp = client.getIp();
        String clientPid = client.getPid();
        StreamObserver<EventMeshMessage> emitter = client.getEventEmitter();
        Map<String, StreamObserver<EventMeshMessage>> emitters = idcEmitters.computeIfAbsent(idc, k -> new HashMap<>());
        emitters.put(clientIp + ":" + clientPid, emitter);
    }

    @Override
    public void deregisterClient(ConsumerGroupClient client) {
        String idc = client.getIdc();
        String clientIp = client.getIp();
        String clientPid = client.getPid();

        Map<String, StreamObserver<EventMeshMessage>> emitters = idcEmitters.get(idc);
        if (emitters == null) {
            return;
        }
        emitters.remove(clientIp + ":" + clientPid);
        if (emitters.size() == 0) {
            idcEmitters.remove(idc);
        }
    }

    @Override
    public int getSize() {
        int total = 0;
        for (Map<String, StreamObserver<EventMeshMessage>> emitters : idcEmitters.values()) {
            total += emitters.size();
        }
        return total;
    }

    @Override
    public String toString() {
        return "StreamConsumeTopicConfig={consumerGroup=" + consumerGroup
            + ",grpcType=" + grpcType
            + ",topic=" + topic + "}";
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

    public GrpcType getGrpcType() {
        return grpcType;
    }

    public Map<String, Map<String, StreamObserver<EventMeshMessage>>> getIdcEmitters() {
        return idcEmitters;
    }
}