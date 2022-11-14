package org.apache.eventmesh.runtime.core.protocol.amqp.downstreamstrategy;

import org.apache.eventmesh.runtime.core.protocol.amqp.consumer.AmqpConsumer;
import org.apache.eventmesh.runtime.core.protocol.amqp.consumer.QueueConsumerMapping;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * randomly select channel
 */
public class RandomDispatchStrategy implements DownstreamDispatchStrategy {
    @Override
    public AmqpConsumer select(String queue, QueueConsumerMapping queueConsumerMapping) {

        Set<AmqpConsumer> consumers = queueConsumerMapping.getConsumers(null, queue);
        if (consumers == null || consumers.size() <= 0) {
            return null;
        }

        List<AmqpConsumer> amqpConsumerList = new ArrayList<>(consumers);
        Collections.shuffle(amqpConsumerList);
        return amqpConsumerList.get(0);
    }
}