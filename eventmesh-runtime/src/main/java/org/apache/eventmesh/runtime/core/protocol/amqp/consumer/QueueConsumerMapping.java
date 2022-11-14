package org.apache.eventmesh.runtime.core.protocol.amqp.consumer;

import org.apache.eventmesh.runtime.core.protocol.amqp.exception.AmqpNotFoundException;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueueConsumerMapping {

    public static Logger logger = LoggerFactory.getLogger(ClientConsumerWrapper.class);

    private ConcurrentHashMap<String, ConcurrentHashMap<String, Set<AmqpConsumer>>> queueConsumerMapping = new ConcurrentHashMap<>();


    public void registerConsumer(String virtualHost, String queue, AmqpConsumer consumer) throws AmqpNotFoundException {

        ConcurrentHashMap<String, Set<AmqpConsumer>> infoMap = queueConsumerMapping.computeIfAbsent(virtualHost, m -> new ConcurrentHashMap<>());
        if (infoMap == null) {
            logger.error("virtualHost not found {}", virtualHost);
            throw new AmqpNotFoundException("vhost not found");
        }
        Set<AmqpConsumer> consumers = infoMap.computeIfAbsent(queue, m -> new HashSet<>());
        consumers.add(consumer);
    }

    public void removeConsumer(String virtualHost, String queue, AmqpConsumer consumer) throws AmqpNotFoundException {

        ConcurrentHashMap<String, Set<AmqpConsumer>> infoMap = queueConsumerMapping.computeIfAbsent(virtualHost, m -> new ConcurrentHashMap<>());
        if (infoMap == null) {
            logger.error("virtualHost not found {}", virtualHost);
            throw new AmqpNotFoundException("vhost not found");
        }
        Set<AmqpConsumer> consumers = infoMap.computeIfAbsent(queue, m -> new HashSet<>());
        consumers.remove(consumer);
    }

    public Set<AmqpConsumer> getConsumers(String virtualHost, String queue) {
        ConcurrentHashMap<String, Set<AmqpConsumer>> infoMap = queueConsumerMapping.computeIfAbsent(virtualHost, m -> new ConcurrentHashMap<>());
        if (infoMap == null) {
            return null;
        }

        return infoMap.get(queue);

    }


}
