package org.apache.eventmesh.runtime.core.protocol.amqp.downstreamstrategy;

import org.apache.eventmesh.runtime.core.protocol.amqp.consumer.AmqpConsumer;
import org.apache.eventmesh.runtime.core.protocol.amqp.consumer.QueueConsumerMapping;
import org.apache.eventmesh.runtime.core.protocol.amqp.exception.AmqpNotFoundException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * randomly select channel
 */
public class RandomDispatchStrategy implements DownstreamDispatchStrategy {
    @Override
    public AmqpConsumer select(String queue, QueueConsumerMapping queueConsumerMapping) {

        Set<AmqpConsumer> consumers = null;
        try {
            consumers = queueConsumerMapping.getConsumers(null, queue);
        } catch (AmqpNotFoundException e) {
            throw new RuntimeException(e);
        }

        List<AmqpConsumer> amqpConsumerList = new ArrayList<>(consumers);
        Collections.shuffle(amqpConsumerList);
        return amqpConsumerList.get(0);
    }
}