package org.apache.eventmesh.runtime.core.protocol.amqp.metadata.manager;

import org.apache.eventmesh.runtime.core.protocol.amqp.exception.AmqpNotFoundException;
import org.apache.eventmesh.runtime.core.protocol.amqp.metadata.model.ExchangeInfo;
import org.apache.eventmesh.runtime.core.protocol.amqp.metadata.model.QueueInfo;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueueManagerMemoryImpl implements QueueManager {

    private final Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private ConcurrentHashMap<String, ConcurrentHashMap<String, QueueInfo>> queueMapping = new ConcurrentHashMap<>();


    @Override
    public void createQueue(String virtualHostName, String queueName, QueueInfo meta) throws AmqpNotFoundException {
        ConcurrentHashMap<String, QueueInfo> infoMap = queueMapping.computeIfAbsent(virtualHostName, m -> new ConcurrentHashMap<>());
        if (infoMap == null) {
            log.error("virtualHost not found {}", virtualHostName);
            throw new AmqpNotFoundException("vhost not found");
        }
        if (!infoMap.containsKey(queueName)) {
            infoMap.put(queueName, meta);
        }
    }


    @Override
    public QueueInfo getQueue(String virtualHostName, String queueName) {
        ConcurrentHashMap<String, QueueInfo> map = queueMapping.get(virtualHostName);
        if (map != null) {
            return map.get(queueName);
        }
        return null;
    }

    @Override
    public void deleteQueue(String virtualHostName, String queueName) {
        ConcurrentHashMap<String, QueueInfo> map = queueMapping.get(virtualHostName);
        if (map != null) {
            map.remove(queueName);
        }
    }

    @Override
    public void queueBind(String virtualHostName, String queue, String exchangeName) throws AmqpNotFoundException {
        QueueInfo queueInfo = getQueue(virtualHostName, queue);
        if (queueInfo == null) {
            throw new AmqpNotFoundException("queue not found");
        }
        queueInfo.addBinding(exchangeName);
    }

    @Override
    public void queueUnBind(String virtualHostName, String queue, String exchangeName) throws AmqpNotFoundException {
        QueueInfo queueInfo = getQueue(virtualHostName, queue);
        if (queueInfo == null) {
            throw new AmqpNotFoundException("queue not found");
        }
        queueInfo.removeBinding(exchangeName);
    }

    @Override
    public void queueUnBindAll(String virtualHostName, String queue) throws AmqpNotFoundException {
        QueueInfo queueInfo = getQueue(virtualHostName, queue);
        if (queueInfo == null) {
            throw new AmqpNotFoundException("queue not found");
        }
        queueInfo.removeAll();
    }

    @Override
    public boolean checkExist(String virtualHostName, String queue) {
        return getQueue(virtualHostName, queue) != null;
    }

    @Override
    public Set<String> getQueueList(String virtualHostName) {
        return queueMapping.get(virtualHostName).keySet();
    }

    @Override
    public Set<String> getBindings(String virtualHost, String queue) {
        QueueInfo queueInfo = getQueue(virtualHost, queue);
        if(queueInfo!=null){
            return queueInfo.getExchanges();
        }
        return null;
    }
}
