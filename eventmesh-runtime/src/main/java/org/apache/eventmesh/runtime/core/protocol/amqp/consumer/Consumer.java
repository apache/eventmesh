package org.apache.eventmesh.runtime.core.protocol.amqp.consumer;

import java.util.List;

public interface Consumer {

    void messageRedeliver(Object messageId);

    void messageRedeliver(List<Object> messageIds);

    /**
     * messageId Not defined yet
     * @param messageId
     * @throws Exception
     */
    void messageAck(Object messageId) throws Exception;

    void close();

    String getConsumerTag();

    String getQueue();

    void notifyConsumer();
}
