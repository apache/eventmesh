package org.apache.eventmesh.runtime.core.protocol.amqp.processor;

import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.AMQPFrame;

import java.util.Map;

public interface ChannelMethodProcessor {

    void receiveChannelFlow(boolean active);

    void receiveChannelFlowOk(boolean active);

    void receiveChannelClose(int replyCode, String replyText, int classId, int methodId);

    void receiveChannelCloseOk();

    void receiveMessageContent(AMQPFrame data);

    void receiveMessageHeader(AMQPFrame frame);

    boolean ignoreAllButCloseOk();

    void receiveBasicNack(long deliveryTag, boolean multiple, boolean requeue);

    void receiveBasicAck(long deliveryTag, boolean multiple);

    void receiveAccessRequest(String realm,
                              boolean exclusive,
                              boolean passive,
                              boolean active,
                              boolean write, boolean read);

    void receiveExchangeDeclare(String exchange,
                                String type,
                                boolean passive,
                                boolean durable,
                                boolean autoDelete, boolean internal, boolean nowait, final Map<String, Object> arguments);

    void receiveExchangeDelete(String exchange, boolean ifUnused, boolean nowait);

    void receiveExchangeBound(String exchange, String routingKey, String queue);

    void receiveQueueDeclare(String queue,
                             boolean passive,
                             boolean durable,
                             boolean exclusive,
                             boolean autoDelete, boolean nowait, Map<String, Object> arguments);

    void receiveQueueBind(String queue,
                          String exchange,
                          String bindingKey,
                          boolean nowait, Map<String, Object> arguments);

    void receiveQueuePurge(String queue, boolean nowait);

    void receiveQueueDelete(String queue, boolean ifUnused, boolean ifEmpty, boolean nowait);

    void receiveQueueUnbind(String queue,
                            String exchange,
                            String bindingKey,
                            Map<String, Object> arguments);

    void receiveBasicRecover(final boolean requeue, boolean sync);

    void receiveBasicQos(long prefetchSize, int prefetchCount, boolean global);

    void receiveBasicConsume(String queue,
                             String consumerTag,
                             boolean noLocal,
                             boolean noAck,
                             boolean exclusive, boolean nowait, Map<String, Object> arguments);

    void receiveBasicCancel(String consumerTag, boolean noWait);

    void receiveBasicPublish(String exchange,
                             String routingKey,
                             boolean mandatory,
                             boolean immediate);

    void receiveBasicGet(String queue, boolean noAck);

    void receiveBasicReject(long deliveryTag, boolean requeue);

    void receiveTxSelect();

    void receiveTxCommit();

    void receiveTxRollback();

    void receiveConfirmSelect(boolean nowait);

}
