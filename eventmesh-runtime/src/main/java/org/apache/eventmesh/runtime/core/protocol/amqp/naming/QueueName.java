package org.apache.eventmesh.runtime.core.protocol.amqp.naming;

public class QueueName {

    private final String queueName;
    private final String virtualHostName;

    public QueueName(String queueName, String virtualHostName) {
        this.queueName = queueName;
        this.virtualHostName = virtualHostName;
    }

    public static QueueName get(String virtualHost, String queueName) {

        return new QueueName(virtualHost, queueName);
    }


}
