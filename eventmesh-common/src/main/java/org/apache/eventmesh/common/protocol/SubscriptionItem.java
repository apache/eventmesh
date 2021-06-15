package org.apache.eventmesh.common.protocol;

public class SubscriptionItem {

    private String topic;

    private SubscriptionMode mode;

    public SubscriptionItem(String topic, SubscriptionMode mode) {
        this.topic = topic;
        this.mode = mode;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public SubscriptionMode getMode() {
        return mode;
    }

    public void setMode(SubscriptionMode mode) {
        this.mode = mode;
    }

    @Override
    public String toString() {
        return "SubscriptionItem{" +
                "topic=" + topic +
                ", mode=" + mode +
                '}';
    }
}


