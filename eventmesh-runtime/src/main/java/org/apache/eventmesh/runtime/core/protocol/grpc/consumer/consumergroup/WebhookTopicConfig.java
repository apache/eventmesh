package org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup;

import io.grpc.stub.StreamObserver;
import org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription.SubscriptionItem.SubscriptionMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WebhookTopicConfig extends ConsumerGroupTopicConfig {
    private final Logger logger = LoggerFactory.getLogger(WebhookTopicConfig.class);

    /**
     * PUSH URL
     * <p>
     * Key: IDC
     * Value: list of URls
     */
    private final Map<String, List<String>> idcUrls = new ConcurrentHashMap<>();

    public WebhookTopicConfig(String consumerGroup, String topic, SubscriptionMode subscriptionMode) {
        super(consumerGroup, topic, subscriptionMode, GrpcType.WEBHOOK);
    }

    @Override
    public synchronized void registerClient(ConsumerGroupClient client) {
        if (!client.getGrpcType().equals(grpcType)) {
            logger.warn("Invalid grpc type: {}, expecting grpc type: {}, can not register client {}",
                client.getGrpcType(), grpcType, client.toString());
            return;
        }
        String idc = client.getIdc();
        String url = client.getUrl();
        List<String> urls = idcUrls.computeIfAbsent(idc, k -> new LinkedList<>());
        if (!urls.contains(url)) {
            urls.add(url);
        }
    }

    @Override
    public void deregisterClient(ConsumerGroupClient client) {
        String idc = client.getIdc();
        String url = client.getUrl();

        List<String> urls = idcUrls.get(idc);
        if (urls == null) {
            return;
        }
        urls.remove(url);
        if (urls.size() == 0) {
            idcUrls.remove(idc);
        }
    }

    @Override
    public int getSize() {
        int total = 0;
        for (List<String> urls : idcUrls.values()) {
            total += urls.size();
        }
        return total;
    }

    @Override
    public String toString() {
        return "WebhookConsumeTopicConfig={consumerGroup=" + consumerGroup
            + ",grpcType=" + grpcType
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

    public GrpcType getGrpcType() {
        return grpcType;
    }

    public Map<String, List<String>> getIdcUrls() {
        return idcUrls;
    }
}