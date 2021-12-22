package org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup;

import org.apache.eventmesh.common.protocol.grpc.protos.Subscription.SubscriptionItem.SubscriptionMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ConsumerGroupTopicConfig {
    private final Logger logger = LoggerFactory.getLogger(ConsumerGroupTopicConfig.class);

    protected final String consumerGroup;

    protected final String topic;

    protected final SubscriptionMode subscriptionMode;

    protected final GrpcType grpcType;

    protected ConsumerGroupTopicConfig(String consumerGroup, String topic, SubscriptionMode subscriptionMode, GrpcType grpcType) {
        this.consumerGroup = consumerGroup;
        this.topic = topic;
        this.subscriptionMode = subscriptionMode;
        this.grpcType = grpcType;
    }

    public static ConsumerGroupTopicConfig buildTopicConfig(String consumerGroup, String topic, SubscriptionMode subscriptionMode,
                                                            GrpcType grpcType) {
        if (GrpcType.STREAM.equals(grpcType)) {
            return new StreamTopicConfig(consumerGroup, topic, subscriptionMode);
        } else {
            return new WebhookTopicConfig(consumerGroup, topic, subscriptionMode);
        }
    }

    public abstract void registerClient(ConsumerGroupClient client);

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
}