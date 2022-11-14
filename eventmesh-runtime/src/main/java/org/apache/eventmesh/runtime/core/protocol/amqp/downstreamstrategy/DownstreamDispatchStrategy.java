package org.apache.eventmesh.runtime.core.protocol.amqp.downstreamstrategy;

import org.apache.eventmesh.runtime.core.protocol.amqp.consumer.AmqpConsumer;
import org.apache.eventmesh.runtime.core.protocol.amqp.consumer.QueueConsumerMapping;

/**
 * used to choose a channel that can consume message
 */
public interface DownstreamDispatchStrategy {
    AmqpConsumer select(String topic, QueueConsumerMapping queueConsumerMapping);
}