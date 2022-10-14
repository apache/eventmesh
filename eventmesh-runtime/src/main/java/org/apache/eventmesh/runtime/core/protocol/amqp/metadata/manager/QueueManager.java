package org.apache.eventmesh.runtime.core.protocol.amqp.metadata.manager;

import org.apache.eventmesh.runtime.core.protocol.amqp.exception.AmqpNotFoundException;
import org.apache.eventmesh.runtime.core.protocol.amqp.metadata.model.QueueInfo;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface QueueManager {

    void createQueue(String virtualHostName, final String queueName, QueueInfo meta) throws AmqpNotFoundException;


    QueueInfo getQueue(String virtualHostName, final String queueName);

    void deleteQueue(String virtualHostName, final String queueName);


    void queueBind(String virtualHostName, String queue, String exchangeName) throws AmqpNotFoundException;


    void queueUnBind(String virtualHostName, final String queue,
                     String exchangeName) throws AmqpNotFoundException;


    void queueUnBindAll(String virtualHostName, final String queue) throws AmqpNotFoundException;

    boolean checkExist(String virtualHostName, final String queue);

    Set<String> getQueueList(String virtualHostName);

    Set<String> getBindings(String virtualHostName, String queue);

}
