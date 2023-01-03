package org.apache.eventmesh.runtime.core.protocol.amqp.consumer;

import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.amqp.AmqpMessage;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.runtime.core.plugin.MQConsumerWrapper;
import org.apache.eventmesh.runtime.core.protocol.amqp.processor.AmqpChannel;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.protocol.ErrorCodes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;

public class AmqpConsumerImpl implements AmqpConsumer {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * each consumer should be a tag that used to identify itself only within a channel.
     * each consumerTag should be unique within a channel.
     */
    private String consumerTag;

    /**
     * id
     */
    private String consumerId;

    private boolean autoAck;

    /**
     * a queue which current consumer wants to consume from
     */
    private String queueName;

    /**
     * amqpChannel that current consumer used
     */
    private AmqpChannel channel;

    /**
     * a map that store all un ack message which has been pushed to client
     */
    private ConcurrentHashMap<String, PushMessageContext> unAckMap = new ConcurrentHashMap<>();

    @Override
    public void pushMessage(PushMessageContext pushMessageContext) {
        // TODO: 2022/10/12 push to client
        AmqpMessage amqpMessage = new AmqpMessage();
        String protocolType = Objects.requireNonNull(pushMessageContext.getCloudEvent().getExtension(Constants.PROTOCOL_TYPE)).toString();
        ProtocolAdaptor protocolAdaptor = ProtocolPluginFactory.getProtocolAdaptor(protocolType);
        try {
            amqpMessage = (AmqpMessage) protocolAdaptor.fromCloudEvent(pushMessageContext.getCloudEvent());
        } catch (ProtocolHandleException e) {
            // TODO: 2022/10/20 exception handle
            throw new RuntimeException(e);
        }
        long deliveryTag = this.channel.getNextDeliveryTag();
        if (!autoAck) {
            addUnAckMsg(deliveryTag, pushMessageContext);
        }

        try {
            channel.getConnection().getAmqpOutputConverter().writeDeliver(amqpMessage, channel.getChannelId(),
                    false, deliveryTag, consumerTag);
        } catch (IOException e) {
            logger.error("sendMessages IOException", e);
            channel.closeChannel(ErrorCodes.INTERNAL_ERROR, "system error");
        }


    }

    @Override
    public void messageRedeliver(Object messageId) {

    }

    @Override
    public void messageRedeliver(List<Object> messageIds) {

    }

    @Override
    public void messageAck(String messageId) throws Exception {
        this.unAckMap.remove(messageId);
        PushMessageContext pushMessageContext = this.unAckMap.get(messageId);
        MQConsumerWrapper consumer = pushMessageContext.getMqConsumerWrapper();
        AbstractContext context = pushMessageContext.getConsumeConcurrentlyContext();
        List<CloudEvent> cloudEventList = new ArrayList<>();
        cloudEventList.add(pushMessageContext.getCloudEvent());
        consumer.updateOffset(cloudEventList, context);
    }

    @Override
    public void close() {

    }

    @Override
    public String getConsumerId() {
        return consumerId;
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

    /**
     * add unacked msg
     */
    private void addUnAckMsg(Long deliveryTag, PushMessageContext pushMessageContext) {
        this.channel.getUnacknowledgedMessageMap().add(deliveryTag, pushMessageContext.getMessageId(), this, 1);
        this.unAckMap.put(pushMessageContext.getMessageId(), pushMessageContext);
    }
}