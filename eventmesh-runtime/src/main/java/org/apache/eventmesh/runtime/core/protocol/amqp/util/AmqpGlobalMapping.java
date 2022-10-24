package org.apache.eventmesh.runtime.core.protocol.amqp.util;

import org.apache.eventmesh.runtime.core.protocol.amqp.consumer.AmqpConsumer;
import org.apache.eventmesh.runtime.core.protocol.amqp.processor.AmqpChannel;
import org.apache.eventmesh.runtime.core.protocol.amqp.processor.AmqpConnection;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AmqpGlobalMapping {

    /**
     * mapping between connection and channel
     */
    private ConcurrentHashMap<AmqpConnection, Set<AmqpChannel>> connection2ChannelMap;

    /**
     * mapping between queue and channel
     */
    private ConcurrentHashMap<String, Set<AmqpChannel>> queue2ChannelMap;

    /**
     * mapping between queue and consumer
     */
    private ConcurrentHashMap<String, Set<AmqpConsumer>> queue2ConsumerMap;

    public ConcurrentHashMap<AmqpConnection, Set<AmqpChannel>> getConnection2ChannelMap() {
        return connection2ChannelMap;
    }

    public ConcurrentHashMap<String, Set<AmqpChannel>> getQueue2ChannelMap() {
        return queue2ChannelMap;
    }

    public ConcurrentHashMap<String, Set<AmqpConsumer>> getQueue2ConsumerMap() {
        return queue2ConsumerMap;
    }
}