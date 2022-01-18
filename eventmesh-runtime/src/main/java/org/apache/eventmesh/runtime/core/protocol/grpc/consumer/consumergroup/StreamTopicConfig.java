package org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup;

import org.apache.eventmesh.common.protocol.grpc.protos.Subscription.SubscriptionItem.SubscriptionMode;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.EventEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StreamTopicConfig extends ConsumerGroupTopicConfig {
    private final Logger logger = LoggerFactory.getLogger(StreamTopicConfig.class);

    /**
     * Key: IDC
     * Value: list of emitters with Client_IP:port
     */
    private final Map<String, Map<String, EventEmitter<EventMeshMessage>>> idcEmitterMap = new ConcurrentHashMap<>();

    /**
     * Key: IDC
     * Value: list of emitters
     */
    private Map<String, List<EventEmitter<EventMeshMessage>>> idcEmitters = new ConcurrentHashMap<>();

    private List<EventEmitter<EventMeshMessage>> totalEmitters = new LinkedList<>();

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
        EventEmitter<EventMeshMessage> emitter = client.getEventEmitter();
        Map<String, EventEmitter<EventMeshMessage>> emitters = idcEmitterMap.computeIfAbsent(idc, k -> new HashMap<>());
        emitters.put(clientIp + ":" + clientPid, emitter);

        idcEmitters = buildIdcEmitter();
        totalEmitters = buildTotalEmitter();
    }

    @Override
    public void deregisterClient(ConsumerGroupClient client) {
        String idc = client.getIdc();
        String clientIp = client.getIp();
        String clientPid = client.getPid();

        Map<String, EventEmitter<EventMeshMessage>> emitters = idcEmitterMap.get(idc);
        if (emitters == null) {
            return;
        }
        emitters.remove(clientIp + ":" + clientPid);
        if (emitters.size() == 0) {
            idcEmitterMap.remove(idc);
        }
        idcEmitters = buildIdcEmitter();
        totalEmitters = buildTotalEmitter();
    }

    @Override
    public int getSize() {
        return totalEmitters.size();
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

    public Map<String, List<EventEmitter<EventMeshMessage>>> getIdcEmitters() {
        return idcEmitters;
    }

    public List<EventEmitter<EventMeshMessage>> getTotalEmitters() {
        return totalEmitters;
    }

    private Map<String, List<EventEmitter<EventMeshMessage>>> buildIdcEmitter() {
        Map<String, List<EventEmitter<EventMeshMessage>>> result = new HashMap<>();
        for (Map.Entry<String, Map<String, EventEmitter<EventMeshMessage>>> entry : idcEmitterMap.entrySet()) {
            List<EventEmitter<EventMeshMessage>> emitterList = new LinkedList<>(entry.getValue().values());
            result.put(entry.getKey(), emitterList);
        }
        return result;
    }

    private List<EventEmitter<EventMeshMessage>> buildTotalEmitter() {
        List<EventEmitter<EventMeshMessage>> emitterList = new LinkedList<>();
        for (List<EventEmitter<EventMeshMessage>> emitters : idcEmitters.values()) {
            emitterList.addAll(emitters);
        }
        return emitterList;
    }
}