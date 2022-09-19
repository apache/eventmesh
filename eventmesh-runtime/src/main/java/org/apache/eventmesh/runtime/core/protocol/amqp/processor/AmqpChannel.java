package org.apache.eventmesh.runtime.core.protocol.amqp.processor;

import com.rabbitmq.client.impl.AMQCommand;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.runtime.core.protocol.amqp.consumer.Consumer;
import org.apache.eventmesh.runtime.core.protocol.amqp.consumer.ConsumerImpl;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.AMQPFrame;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.protocol.ErrorCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class AmqpChannel implements ChannelMethodProcessor{

    private final Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private  int channelId;
    private  AmqpConnection connection;
    private  long amqpMaxMessageSize;
    /**
     * Maps from consumer tag to consumers instance.
     */
    private final Map<String, Consumer> tag2ConsumersMap = new ConcurrentHashMap<>();

    /**
     * The current message - which may be partial in the sense that not all frames have been received yet - which has
     * been received by this channel. As the frames are received the message gets updated and once all frames have been
     * received the message can then be routed.
     */
    private AMQCommand currentMessage;

    /**
     * This tag is unique per subscription to a queue. The server returns this in response to a basic.consume request.
     */
    private AtomicInteger consumerTag = new AtomicInteger(0);

    /**
     * The delivery tag is unique per channel. This is pre-incremented before putting into the deliver frame so that
     * value of this represents the <b>last</b> tag sent out.
     */
    private AtomicLong deliveryTag = new AtomicLong(0);



    @Override
    public void receiveChannelFlow(boolean active) {

    }

    @Override
    public void receiveChannelFlowOk(boolean active) {

    }

    @Override
    public void receiveChannelClose(int replyCode, String replyText, int classId, int methodId) {

    }

    @Override
    public void receiveChannelCloseOk() {

    }

    @Override
    public void receiveMessageContent(AMQPFrame data) {
        if (log.isDebugEnabled()) {
            try {
                log.debug("receive messageContent[data:{}]", new String(data.getData(), "UTF8"));
            } catch (UnsupportedEncodingException e) {
                log.error("Failed to encode data:{}", e);
            }
        }

        if (hasCurrentMessage()) {
//            try {
//                if (currentMessage.handleFrame(data)) {
//                    processSendMessage();
//                }
//            } catch (IOException e) {
//                log.error("receiveMessageContent exception {}", e);
//                closeChannel(ErrorCodes.COMMAND_INVALID,
//                        "Attempt to send a content  not valid");
//            } catch (Exception e) {
//                log.error("receiveMessageContent exception {}", e);
//                closeChannel(ErrorCodes.SYNTAX_ERROR,
//                        "system error");
//            }
        } else {
            closeChannel(ErrorCodes.COMMAND_INVALID,
                    "Attempt to send a content without first sending a publish frame");
        }
    }

    @Override
    public void receiveMessageHeader(AMQPFrame frame) {
        if (log.isDebugEnabled()) {
            log.debug("recv[{}] message header[{}]", channelId, frame);
        }

        if (hasCurrentMessage()) {
//            try {
//                if (currentMessage.handleFrame(frame)) {
//                    processSendMessage();
//                }
//            } catch (IOException e) {
//                log.error("receiveMessageHeader exce {}", e.getMessage());
//                closeChannel(ErrorCodes.COMMAND_INVALID,
//                        "Attempt to send a content  not valid");
//            } catch (Exception e) {
//                log.error("receiveMessageHeader exce {}", e.getMessage());
//                closeChannel(ErrorCodes.SYNTAX_ERROR,
//                        "system error");
//            }

            long bodySize = currentMessage.getContentHeader().getBodySize();
            if (bodySize > amqpMaxMessageSize) {
                log.error("RECV[{}] too large message bodySize {}", channelId, bodySize);
                closeChannel(ErrorCodes.MESSAGE_TOO_LARGE,
                        "Message size of " + bodySize + " greater than allowed maximum of " + amqpMaxMessageSize);
            }

        } else {
            closeChannel(ErrorCodes.COMMAND_INVALID,
                    "Attempt to send a content without first sending a publish frame");
        }
    }

    /**
     * send message
     */
    private void processSendMessage(){





    }




    @Override
    public boolean ignoreAllButCloseOk() {
        return false;
    }

    @Override
    public void receiveBasicNack(long deliveryTag, boolean multiple, boolean requeue) {

    }

    @Override
    public void receiveBasicAck(long deliveryTag, boolean multiple) {

    }

    @Override
    public void receiveAccessRequest(String realm, boolean exclusive, boolean passive, boolean active, boolean write, boolean read) {

    }

    @Override
    public void receiveExchangeDeclare(String exchange, String type, boolean passive, boolean durable, boolean autoDelete, boolean internal, boolean nowait, Map<String, Object> arguments) {

    }

    @Override
    public void receiveExchangeDelete(String exchange, boolean ifUnused, boolean nowait) {

    }

    @Override
    public void receiveExchangeBound(String exchange, String routingKey, String queue) {

    }

    @Override
    public void receiveQueueDeclare(String queue, boolean passive, boolean durable, boolean exclusive, boolean autoDelete, boolean nowait, Map<String, Object> arguments) {

    }

    @Override
    public void receiveQueueBind(String queue, String exchange, String bindingKey, boolean nowait, Map<String, Object> arguments) {

    }

    @Override
    public void receiveQueuePurge(String queue, boolean nowait) {

    }

    @Override
    public void receiveQueueDelete(String queue, boolean ifUnused, boolean ifEmpty, boolean nowait) {

    }

    @Override
    public void receiveQueueUnbind(String queue, String exchange, String bindingKey, Map<String, Object> arguments) {

    }

    @Override
    public void receiveBasicRecover(boolean requeue, boolean sync) {

    }

    @Override
    public void receiveBasicQos(long prefetchSize, int prefetchCount, boolean global) {

    }

    @Override
    public void receiveBasicConsume(String queue, String consumerTag, boolean noLocal, boolean noAck, boolean exclusive, boolean nowait, Map<String, Object> arguments) {
        log.info("RECV BasicConsume[queue:{} consumerTag:{} noLocal:{} noAck:{} exclusive:{} nowait:{}"
                + " arguments:{}]",  queue, consumerTag, noLocal, noAck, exclusive, nowait, arguments);

        // TODO check queue exist

        final String consumerTag1;
        if (StringUtils.isBlank(consumerTag)) {
            consumerTag1 = "consumerTag_" + getNextConsumerTag();
        } else {
            consumerTag1 = consumerTag;
        }

        tag2ConsumersMap.computeIfAbsent(consumerTag1, (c) ->new ConsumerImpl());
        if (!nowait) {
            //connection.writeMethod(connection.getCommandFactory().createBasicConsumeOkBody(consumer.getConsumerTag()), channelId);
        }
    }

    @Override
    public void receiveBasicCancel(String consumerTag, boolean noWait) {

    }

    @Override
    public void receiveBasicPublish(String exchange, String routingKey, boolean mandatory, boolean immediate) {

    }

    @Override
    public void receiveBasicGet(String queue, boolean noAck) {

    }

    @Override
    public void receiveBasicReject(long deliveryTag, boolean requeue) {

    }

    @Override
    public void receiveTxSelect() {

    }

    @Override
    public void receiveTxCommit() {

    }

    @Override
    public void receiveTxRollback() {

    }

    @Override
    public void receiveConfirmSelect(boolean nowait) {

    }

    public void closeChannel(int cause, final String message) {
        // TODO
    }


    private boolean hasCurrentMessage() {
        return currentMessage != null;
    }

    public long getNextDeliveryTag() {
        return deliveryTag.incrementAndGet();
    }

    private int getNextConsumerTag() {
        return consumerTag.incrementAndGet();
    }
}
