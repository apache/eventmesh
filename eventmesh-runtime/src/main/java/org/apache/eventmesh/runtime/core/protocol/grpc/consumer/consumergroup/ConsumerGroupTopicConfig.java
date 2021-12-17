package org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup;

import com.google.common.collect.Maps;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription.SubscriptionItem.SubscriptionMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ConsumerGroupTopicConfig {
    public static Logger logger = LoggerFactory.getLogger(ConsumerGroupTopicConfig.class);

    private String consumerGroup;

    private String topic;

    private SubscriptionMode subscriptionMode;

    /**
     * PUSH URL
     * <p>
     * Key: IDC
     * Value: list of URls
     */
    private Map<String, List<String>> idcUrls = Maps.newConcurrentMap();

    public ConsumerGroupTopicConfig(String consumerGroup, String topic, SubscriptionMode subscriptionMode) {
        this.consumerGroup = consumerGroup;
        this.topic = topic;
        this.subscriptionMode = subscriptionMode;
    }

    public synchronized void addUrl(String idc, String url) {
        List<String> urls = idcUrls.get(idc);
        if (urls == null) {
            urls = new ArrayList<>();
            urls.add(url);
            idcUrls.put(idc, urls);
        } else if (!urls.contains(url)) {
            urls.add(url);
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

    public Map<String, List<String>> getIdcUrls() {
        return idcUrls;
    }
}