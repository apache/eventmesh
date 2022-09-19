package org.apache.eventmesh.runtime.core.protocol.amqp.consumer;

import java.util.List;

public class ConsumerImpl implements Consumer{
    @Override
    public void messageRedeliver(Object messageId) {

    }

    @Override
    public void messageRedeliver(List<Object> messageIds) {

    }

    @Override
    public void messageAck(Object messageId) throws Exception {

    }

    @Override
    public void close() {

    }

    @Override
    public String getConsumerTag() {
        return null;
    }

    @Override
    public String getQueue() {
        return null;
    }

    @Override
    public void notifyConsumer() {

    }
}
