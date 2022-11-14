package org.apache.eventmesh.runtime.core.protocol.amqp.producer;

import org.apache.eventmesh.common.protocol.amqp.AmqpMessage;

import java.util.Map;
import java.util.Set;

public class PutMessageContext {

    private String exchangeName;
    private String routingKey;
    private Map<String, Object> props;
    private AmqpMessage message;
    // temporary impl
    Set<String> queues;


    public PutMessageContext(String exchangeName, String routingKey, Map<String, Object> props,
                             AmqpMessage message, Set<String> queues) {
        this.exchangeName = exchangeName;
        this.routingKey = routingKey;
        this.props = props;
        this.message = message;
        this.queues = queues;
    }

    public String getExchangeName() {
        return exchangeName;
    }

    public void setExchangeName(String exchangeName) {
        this.exchangeName = exchangeName;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }

    public Map<String, Object> getProps() {
        return props;
    }

    public void setProps(Map<String, Object> props) {
        this.props = props;
    }

    public AmqpMessage getMessage() {
        return message;
    }

    public void setMessage(AmqpMessage message) {
        this.message = message;
    }

    public Set<String> getQueues() {
        return queues;
    }

    public void setQueues(Set<String> queues) {
        this.queues = queues;
    }
}
