package org.apache.eventmesh.runtime.core.protocol.amqp.downstreamstrategy;

import org.apache.eventmesh.runtime.core.protocol.amqp.consumer.AmqpConsumer;
import org.apache.eventmesh.runtime.core.protocol.amqp.processor.AmqpChannel;
import org.apache.eventmesh.runtime.core.protocol.amqp.util.AmqpGlobalMapping;

/**
 * used to choose a channel that can consume message
 */
public interface DownstreamDispatchStrategy {
    AmqpConsumer select(String topic, AmqpGlobalMapping amqpGlobalMapping);
}