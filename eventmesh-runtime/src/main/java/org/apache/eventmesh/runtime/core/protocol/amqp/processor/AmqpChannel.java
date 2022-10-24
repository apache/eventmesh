/**
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.apache.eventmesh.runtime.core.protocol.amqp.processor;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.impl.AMQCommand;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.runtime.boot.EventMeshAmqpServer;
import org.apache.eventmesh.runtime.configuration.EventMeshAmqpConfiguration;
import org.apache.eventmesh.runtime.core.protocol.amqp.exception.AmqpException;
import org.apache.eventmesh.runtime.core.protocol.amqp.exchange.ExchangeDefaults;
import org.apache.eventmesh.runtime.core.protocol.amqp.metadata.model.ExchangeInfo;
import org.apache.eventmesh.runtime.core.protocol.amqp.metadata.model.QueueInfo;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.AMQPFrame;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.protocol.ErrorCodes;
import org.apache.eventmesh.runtime.core.protocol.amqp.service.ExchangeService;
import org.apache.eventmesh.runtime.core.protocol.amqp.service.QueueService;
import org.apache.eventmesh.runtime.core.protocol.amqp.util.NameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.apache.eventmesh.runtime.core.protocol.amqp.remoting.protocol.ErrorCodes.INTERNAL_ERROR;
import static org.apache.eventmesh.runtime.core.protocol.amqp.remoting.protocol.ErrorCodes.NOT_FOUND;

/**
 * Amqp Channel level method processor.
 */

public class AmqpChannel implements ChannelMethodProcessor {

    private final Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private final int channelId;
    private final AmqpConnection connection;
    private final AtomicBoolean blocking = new AtomicBoolean(false);
    private final AtomicBoolean closing = new AtomicBoolean(false);
    private AtomicLong confirmedMessageCounter = new AtomicLong(0);
    private boolean confirmOnPublish;
    /**
     * A channel has a default queue (the last declared) that is used when no queue name is explicitly set.
     */
    private volatile QueueInfo defaultQueue;

    //private final UnacknowledgedMessageMap unacknowledgedMessageMap;

//    /**
//     * Maps from consumer tag to consumers instance.
//     */
//    private final Map<String, CompletableFuture<Consumer>> tag2ConsumersMap = new ConcurrentHashMap<>();

    private List<CompletableFuture<Void>> pendingPublishList = new CopyOnWriteArrayList<>();

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
    private ExchangeService exchangeService;
    private QueueService queueService;
    //private AmqpZooKeeperCacheService localZooKeeperCacheService;
    private EventMeshAmqpConfiguration amqpConfiguration;
    private EventMeshAmqpServer amqpServer;
    private AtomicInteger unConfirmedMessageCount = new AtomicInteger(0);
    private long lastStatTimestamp = System.nanoTime();
    private final long amqpMaxMessageSize;
    private static final int StatsPeriodSeconds = 1;

    private String virtualHostName;

    public AmqpChannel(int channelId, AmqpConnection connection) {
        this.channelId = channelId;
        this.connection = connection;
        this.amqpServer = connection.getAmqpServer();
        this.exchangeService = amqpServer.getExchangeService();
        this.queueService = amqpServer.getQueueService();
        this.amqpConfiguration = amqpServer.getEventMeshAmqpConfiguration();
        this.virtualHostName = connection.getVirtualHostName();
        this.amqpMaxMessageSize = amqpServer.getEventMeshAmqpConfiguration().maxMessageSize;
    }


    @Override
    public void receiveAccessRequest(String realm, boolean exclusive, boolean passive, boolean active,
                                     boolean write, boolean read) {
        log.info("RECV[{}] AccessRequest[ realm: {}, exclusive: {}, passive: {}, active: {}, write: {}, read: {} ]",
            getCurrentEnv(), realm, exclusive, passive, active, write, read);

        // We don't implement access control class, but to keep clients happy that expect it always use the "0" ticket.
        connection.writeMethod(connection.getCommandFactory().createAccessRequestOkBody(0), channelId);

    }

    @Override
    public void receiveExchangeDeclare(String exchange, String type, boolean passive, boolean durable,
                                       boolean autoDelete, boolean internal, boolean nowait, Map<String, Object> arguments) {
        log.info(
            "RECV[{}] ExchangeDeclare[ exchange: {}, type: {}, passive: {}, durable: {}, autoDelete: {}, internal: {}, nowait: {}, arguments: {} ]",
            getCurrentEnv(), exchange, type, passive, durable, autoDelete, internal, nowait, arguments);
        if (StringUtils.isNotBlank(exchange)) {
            try {
                NameUtils.checkName(exchange);
            } catch (IllegalArgumentException e) {
                log.error("[{}] Exchange Name is illegal：{}", getCurrentEnv(), exchange, e);
                closeChannel(ErrorCodes.ARGUMENT_INVALID, e.getMessage());
                return;
            }
        }
        if (passive) {
            ExchangeInfo exchangeInfo = getExchangeInfo(exchange);
            if (exchangeInfo == null) {
                closeChannel(NOT_FOUND, "can not found exchange:" + exchange);
            } else {
                if (!nowait) {
                    final AMQP.Exchange.DeclareOk declareOkBody = connection.getCommandFactory().createExchangeDeclareOkBody();
                    connection.writeMethod(declareOkBody, channelId);
                }
            }
            return;
        }


        try {
            exchangeService.exchangeDeclare(connection.getVirtualHostName(), exchange, type, durable,
                autoDelete, internal, arguments);
            log.info("[{}]  {}exchangeDeclare success ", getCurrentEnv(), exchange);
            if (!nowait) {
                final AMQP.Exchange.DeclareOk declareOkBody = connection.getCommandFactory().createExchangeDeclareOkBody();
                connection.writeMethod(declareOkBody, channelId);
            }
        } catch (AmqpException e) {
            log.info("[{}]  {}exchangeDeclare failed ", getCurrentEnv(), exchange, e);
            processChannelCommandException(e);
        }
    }

    @Override
    public void receiveExchangeDelete(String exchange, boolean ifUnused, boolean nowait) {
        log.info("RECV[{}] ExchangeDelete[ exchange: {}, ifUnused: {}, nowait:{} ]", getCurrentEnv(), exchange,
            ifUnused, nowait);

        try {
            exchangeService.exchangeDelete(connection.getVirtualHostName(), exchange, ifUnused);
            log.info("[{}]  {} ExchangeDelete success ", getCurrentEnv(), exchange);
            if (!nowait) {
                AMQP.Exchange.DeleteOk deleteOk = connection.getCommandFactory().createExchangeDeleteOkBody();
                connection.writeMethod(deleteOk, channelId);
            }
        } catch (AmqpException e) {
            log.info("[{}]  {} ExchangeDelete failed ", getCurrentEnv(), exchange, e);
            processChannelCommandException(e);
        }
    }

    @Override
    public void receiveExchangeBound(String exchange, String routingKey, String queueName) {

        connection.writeMethod(connection.getCommandFactory().createExchangeBindOkBody(), channelId);

    }

    @Override
    public void receiveQueueDeclare(String queue, boolean passive, boolean durable, boolean exclusive,
                                    boolean autoDelete, boolean nowait, Map<String, Object> arguments) {
        log.info("RECV[{}] QueueDeclare[ queue: {}, passive: {}, durable:{}, "
                + "exclusive:{}, autoDelete:{}, nowait:{}, arguments:{} ]",
            getCurrentEnv(), queue, passive, durable, exclusive, autoDelete, nowait, arguments);
        if (StringUtils.isNotBlank(queue)) {
            try {
                NameUtils.checkName(queue);
            } catch (IllegalArgumentException e) {
                log.error("Name is illegal:{}", queue, e);
                closeChannel(ErrorCodes.ARGUMENT_INVALID, e.getMessage());
                return;
            }
        }

        QueueInfo queueInfo = getQueueInfo(queue);

        if (checkExclusiveQueue(queueInfo, connection.getConnectionId())) {
            log.error("checkExclusiveQueue failed {},{}", getCurrentEnv(), queueInfo);
            closeChannel(ErrorCodes.ALREADY_EXISTS, "Exclusive queue can not be used from other connection, queueName:"
                + queueInfo.getQueueName());
            return;
        }

        if (passive) {
            if (queueInfo == null) {
                closeChannel(NOT_FOUND, "can not found queue:" + queue);
            } else {
                if (!nowait) {
                    connection.writeMethod(connection.getCommandFactory().createQueueDeclareOkBody(queueInfo.getQueueName(),
                        0, 0), channelId);
                }
            }
            return;
        }


        try {
            QueueInfo q = queueService.queueDeclare(connection.getConnectionId(), connection.getVirtualHostName(), queue,
                durable, exclusive, autoDelete, arguments);
            log.info("[{}]  {} queueDeclare success", getCurrentEnv(), queue);
            setDefaultQueue(q);
            if (!nowait) {
                connection.writeMethod(connection.getCommandFactory().createQueueDeclareOkBody(q.getQueueName(),
                    0, 0), channelId);
            }
        } catch (AmqpException e) {
            log.info("[{}]  {} queueDeclare failed", getCurrentEnv(), queue, e);
            processChannelCommandException(e);
        }
    }

    @Override
    public void receiveQueueBind(String queue, String exchange, String bindingKey,
                                 boolean nowait, Map<String, Object> argumentsTable) {
        log.info("RECV[{}] QueueBind[ queue: {}, exchange: {}, bindingKey:{}, nowait:{}, arguments:{} ]",
            getCurrentEnv(), queue, exchange, bindingKey, nowait, argumentsTable);

        if (StringUtils.isNotBlank(bindingKey)) {
            try {
                NameUtils.checkName(bindingKey);
            } catch (IllegalArgumentException e) {
                log.error("bindingKey is illegal:{} {}", queue, bindingKey, e);
                closeChannel(ErrorCodes.ARGUMENT_INVALID, e.getMessage());
                return;
            }
        }

        if (checkExclusiveQueue(queue, connection.getConnectionId())) {
            return;
        }

        try {
            queueService.queueBind(connection.getVirtualHostName(), getDefaultQueue(), queue, exchange,
                bindingKey, argumentsTable);
            log.info("[{}] Success to bind exchange:{} to queue:{}", getCurrentEnv(), exchange, queue);
            if (!nowait) {
                connection.writeMethod(connection.getCommandFactory().createQueueBindOkBody(), channelId);
            }
        } catch (AmqpException e) {
            log.info("[{}] failed to bind exchange:{} to queue:{}", getCurrentEnv(), exchange, queue, e);
            processChannelCommandException(e);
        }
    }

    @Override
    public void receiveQueuePurge(String queue, boolean nowait) {
        log.info("RECV[{}] QueuePurge[ queue: {}, nowait:{} ]", getCurrentEnv(), queue, nowait);
        if (checkExclusiveQueue(queue, connection.getConnectionId())) {
            return;
        }

        queueService.queuePurge(connection.getVirtualHostName(), queue);
        log.info("[{}] success to purge queue:{}", getCurrentEnv(), queue);
        if (!nowait) {
            connection.writeMethod(connection.getCommandFactory().createQueuePurgeOkBody(0), channelId);
        }

    }

    @Override
    public void receiveQueueDelete(String queue, boolean ifUnused, boolean ifEmpty, boolean nowait) {
        log.info("RECV[{}] QueueDelete[ queue: {}, ifUnused:{}, ifEmpty:{}, nowait:{} ]", getCurrentEnv(), queue,
            ifUnused, ifEmpty, nowait);

        if (checkExclusiveQueue(queue, connection.getConnectionId())) {
            return;
        }

        try {
            queueService.queueDelete(connection.getVirtualHostName(), getDefaultQueue(), queue, ifUnused, ifEmpty);
            log.info("[{}] success to delete queue:{}", getCurrentEnv(), queue);
            if (!nowait) {
                connection.writeMethod(connection.getCommandFactory().createQueueDeleteOkBody(0), channelId);
            }
        } catch (AmqpException e) {
            log.error("[{}] Failed to delete queue:{}", getCurrentEnv(), queue, e);
            processChannelCommandException(e);
        }
    }

    @Override
    public void receiveQueueUnbind(String queue, String exchange, String bindingKey,
                                   Map<String, Object> arguments) {
        log.info("RECV[{}] QueueUnbind[ queue: {}, exchange:{}, bindingKey:{}, arguments:{} ]", getCurrentEnv(), queue,
            exchange, bindingKey, arguments);
        if (checkExclusiveQueue(queue, connection.getConnectionId())) {
            return;
        }

        try {
            queueService.queueUnbind(connection.getVirtualHostName(), queue, exchange, bindingKey, arguments);
            log.info("[{}] success to unbind queue:{}, exchange:{}", getCurrentEnv(), queue, exchange);
            connection.writeMethod(connection.getCommandFactory().createQueueUnbindOkBody(), channelId);
        } catch (AmqpException e) {
            log.error("[{}] Failed to unbind queue:{}, exchange:{}", getCurrentEnv(), queue, exchange, e);
            processChannelCommandException(e);
        }

    }

    @Override
    public void receiveBasicQos(long prefetchSize, int prefetchCount, boolean global) {
        log.info("RECV[{}] BasicQos[prefetchSize: {} prefetchCount: {} global: {}]",
            getCurrentEnv(), prefetchSize, prefetchCount, global);
        if (prefetchSize > 0) {
            closeChannel(ErrorCodes.NOT_IMPLEMENTED, "prefetchSize not supported ");
            return;
        }
        //creditManager.setCreditLimits(0, prefetchCount);
        connection.writeMethod(connection.getCommandFactory().createBasicQosOkBody(), channelId);
    }

    @Override
    public void receiveBasicConsume(String queue, String consumerTag,
                                    boolean noLocal, boolean noAck, boolean exclusive,
                                    boolean nowait, Map<String, Object> arguments) {

    }

    @Override
    public void receiveBasicCancel(String consumerTag, boolean noWait) {

    }

    private void setPublishFrame(AMQP.Basic.Publish publishFrame) {
        currentMessage = new AMQCommand(publishFrame);
    }

    @Override
    public void receiveBasicGet(String queue, boolean noAck) {


    }


    @Override
    public void receiveChannelFlow(boolean active) {
        if (log.isDebugEnabled()) {
            log.debug("RECV[{}] ChannelFlow[active: {}]", channelId, active);
        }
        // TODO channelFlow process
        connection.writeMethod(connection.getCommandFactory().createChannelFlowOkBody(true), channelId);
    }

    @Override
    public void receiveChannelFlowOk(boolean active) {
        if (log.isDebugEnabled()) {
            log.debug("RECV[{}] ChannelFlowOk[active: {}]", channelId, active);
        }
    }

    @Override
    public void receiveChannelClose(int replyCode, String replyText, int classId, int methodId) {
        log.info("RECV[{}] ChannelClose[replyCode: {} replyText: {} classId: {} methodId: {}",
            getCurrentEnv(), replyCode, replyText, classId, methodId);
        // TODO Process outstanding client requests
        processAsync();
        connection.closeChannel(this);
        connection.writeMethod(connection.getCommandFactory().createChannelCloseOkBody(), channelId);
    }

    @Override
    public void receiveChannelCloseOk() {
        if (log.isDebugEnabled()) {
            log.debug("RECV[ {} ] ChannelCloseOk", channelId);
        }

        connection.closeChannelOk(getChannelId());
    }

    private boolean hasCurrentMessage() {
        return currentMessage != null;
    }

    @Override
    public void receiveBasicPublish(String exchange, String routingKey, boolean mandatory,
                                    boolean immediate) {
        if (log.isDebugEnabled()) {
            log.debug("RECV[{}] BasicPublish[exchange: {} routingKey: {} mandatory: {} immediate: {}]",
                getCurrentEnv(), exchange, routingKey, mandatory, immediate);
        }
        String exchangeName;
        if (isDefaultExchange(exchange)) {
            exchangeName = ExchangeDefaults.DEFAULT_EXCHANGE_NAME_DURABLE;
        } else {
            exchangeName = exchange;
        }

//        if (getExchangeInfo(exchangeName) == null) {
//            closeChannel(NOT_FOUND, "exchange:" + exchangeName + " not find");
//            return;
//        }

        setPublishFrame(connection.getCommandFactory().createBasicPublishBody(0, exchangeName, routingKey, mandatory, immediate));
    }

    @Override
    public void receiveMessageContent(AMQPFrame frame) {
        if (log.isDebugEnabled()) {
            try {
                log.debug("RECV[{}] MessageContent[data:{}]", getCurrentEnv(), new String(frame.getData(), "UTF8"));
            } catch (UnsupportedEncodingException e) {
                log.error("Failed to encode data:{}", getCurrentEnv(), e);
            }
        }

        if (hasCurrentMessage()) {
//            try {
////                if (currentMessage.handleFrame(frame)) {
////                    deliverCurrentMessageIfComplete();
////                }
//            } catch (IOException e) {
//                log.error("receiveMessageContent exception {}", e);
//                closeChannel(ErrorCodes.COMMAND_INVALID,
//                    "Attempt to send a content  not valid");
//            } catch (Exception e) {
//                log.error("receiveMessageContent exception {}", e);
//                closeChannel(ErrorCodes.SYNTAX_ERROR,
//                    "system error");
//            }
        } else {
            closeChannel(ErrorCodes.COMMAND_INVALID,
                "Attempt to send a content without first sending a publish frame");
        }
    }

    @Override
    public void receiveMessageHeader(AMQPFrame frame) {
        if (log.isDebugEnabled()) {
            log.debug("RECV[{}] MessageHeader[{}]", channelId, frame);
        }

        if (hasCurrentMessage()) {
//            try {
////                if (currentMessage.handleFrame(frame)) {
////                    deliverCurrentMessageIfComplete();
////                }
//            } catch (IOException e) {
//                log.error("receiveMessageHeader exce {}", e.getMessage());
//                closeChannel(ErrorCodes.COMMAND_INVALID,
//                    "Attempt to send a content  not valid");
//            } catch (Exception e) {
//                log.error("receiveMessageHeader exce {}", e.getMessage());
//                closeChannel(ErrorCodes.SYNTAX_ERROR,
//                    "system error");
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

    private void deliverCurrentMessageIfComplete() throws Exception {


    }

    private boolean putMsgCheck(Set<String> queues) {

        for (String queue : queues) {
            if (!checkQueueExist(queue)) {
                return false;
            }
        }
        return true;
    }


    @Override
    public boolean ignoreAllButCloseOk() {
        return false;
    }

    @Override
    public void receiveBasicNack(long deliveryTag, boolean multiple, boolean requeue) {

    }


    @Override
    public void receiveBasicRecover(boolean requeue, boolean sync) {

    }


    @Override
    public void receiveBasicAck(long deliveryTag, boolean multiple) {

    }


    @Override
    public void receiveBasicReject(long deliveryTag, boolean requeue) {

    }

    @Override
    public void receiveTxSelect() {
        if (log.isDebugEnabled()) {
            log.debug("RECV[{}] TxSelect", getCurrentEnv());
        }
        // TODO txSelect process
        connection.writeMethod(connection.getCommandFactory().createTxSelectOkBody(), channelId);
    }

    @Override
    public void receiveTxCommit() {
        if (log.isDebugEnabled()) {
            log.debug("RECV[{}] TxCommit", getCurrentEnv());
        }
        // TODO txCommit process
        connection.writeMethod(connection.getCommandFactory().createTxCommitOkBody(), channelId);
    }

    @Override
    public void receiveTxRollback() {
        if (log.isDebugEnabled()) {
            log.debug("RECV[{}] TxRollback", getCurrentEnv());
        }
        // TODO txRollback process
        connection.writeMethod(connection.getCommandFactory().createTxRollbackOkBody(), channelId);
    }

    @Override
    public void receiveConfirmSelect(boolean nowait) {
        if (log.isDebugEnabled()) {
            log.debug("RECV[{}] ConfirmSelect [ nowait: {} ]", getCurrentEnv(), nowait);
        }
        confirmOnPublish = true;

        if (!nowait) {
            connection.writeMethod(connection.getCommandFactory().createConfirmSelectOkBody(), channelId);
        }
    }

    public void receivedComplete() {
        processAsync();
    }

    private void sendChannelClose(int cause, final String message) {
        connection.closeChannelAndWriteFrame(this, cause, message);
    }

    public void processAsync() {
//        if (!pendingPublishList.isEmpty()) {
//            log.info("[{}] closing messageStore, and pendingPublishList size is:{}", getCurrentEnv(), pendingPublishList.size());
//            FutureUtil.waitForAll(pendingPublishList).whenComplete((aVoid, ex) -> {
//                if (ex != null) {
//                    log.error("[{}] Failed to complete publish.", getCurrentEnv(), ex);
//                } else {
//                    log.info("[{}] pendingPublishList had all done.", getCurrentEnv());
//                    pendingPublishList.clear();
//                }
//            });
//        }
    }

    public void close() {
        // TODO
        if (!closing.compareAndSet(false, true)) {
            //Channel is already closing
            return;
        }

//        unsubscribeConsumerAll();
        // TODO need to delete exclusive queues in this channel.
        setDefaultQueue(null);
    }

    public synchronized void block() {
        // TODO
    }

    public synchronized void unblock() {
        // TODO
    }

    public int getChannelId() {
        return channelId;
    }

    public boolean isClosing() {
        return closing.get() || connection.isClosing();
    }

    private boolean isDefaultExchange(final String exchangeName) {
        return StringUtils.isBlank(exchangeName);
    }

    public void closeChannel(int cause, final String message) {
//        if (closing.get()) {
//            if (log.isDebugEnabled()) {
//                log.debug("Channel is already closing id {} ", channelId);
//            }
//            return;
//        }
        connection.closeChannelAndWriteFrame(this, cause, message);
    }

    public long getNextDeliveryTag() {
        return deliveryTag.incrementAndGet();
    }

    private int getNextConsumerTag() {
        return consumerTag.incrementAndGet();
    }

    private long getNextConfirmedCounter() {
        return confirmedMessageCounter.incrementAndGet();
    }

    public AmqpConnection getConnection() {
        return connection;
    }


    protected void setDefaultQueue(QueueInfo queue) {
        defaultQueue = queue;
    }

    protected QueueInfo getDefaultQueue() {
        return defaultQueue;
    }

    public String getExchangeType(String exchangeName) {
        if (StringUtils.isBlank(exchangeName)) {
            exchangeName = ExchangeDefaults.DIRECT_EXCHANGE_NAME;
        }
        switch (exchangeName) {
            case ExchangeDefaults.DIRECT_EXCHANGE_NAME:
                return ExchangeDefaults.DIRECT_EXCHANGE_CLASS;
            case ExchangeDefaults.FANOUT_EXCHANGE_NAME:
                return ExchangeDefaults.FANOUT_EXCHANGE_CLASS;
            case ExchangeDefaults.TOPIC_EXCHANGE_NAME:
                return ExchangeDefaults.TOPIC_EXCHANGE_CLASS;
            default:
                return "";
        }
    }


    private boolean checkExclusiveQueue(String queue, String connectionId) {
        if (StringUtils.isBlank(queue)) {
            return false;
        }
        AtomicBoolean isExclusive = new AtomicBoolean(false);
        QueueInfo amqpQueue = getQueueInfo(queue);

        if (amqpQueue != null && amqpQueue.isExclusive()
            && !amqpQueue.getConnectionId().equals(connectionId)) {
            isExclusive.set(true);
            String message = "Exclusive queue can not be used from other connection, queueName:"
                + amqpQueue.getQueueName();
            log.error("{},{}", getCurrentEnv(), message);
            closeChannel(ErrorCodes.ALREADY_EXISTS, message);
        }
        return isExclusive.get();
    }

    private boolean checkExclusiveQueue(QueueInfo queueInfo, String connectionId) {

        if (queueInfo != null && queueInfo.isExclusive()
            && !queueInfo.getConnectionId().equals(connectionId)) {
            return true;
        }
        return false;
    }

    private boolean checkQueueExist(String queueName) {
        try {
            return queueService.checkExist(virtualHostName, queueName);
        } catch (Exception e) {
            log.error("amqpChannel checkQueueExist error {} ", getCurrentEnv(), e);
            return false;
        }
    }

    private boolean checkExchangeExist(String exchangeName) {
        try {
            return exchangeService.checkExchangeExist(virtualHostName, exchangeName);
        } catch (Exception e) {
            log.error("amqpChannel checkExchangeExist error {} ", getCurrentEnv(), e);
            return false;
        }
    }

    private QueueInfo getQueueInfo(String queueName) {
        try {
            return queueService.getQueue(virtualHostName, queueName);
        } catch (Exception e) {
            log.error("amqpChannel getQueueInfo error {} ", getCurrentEnv(), e);
            return null;
        }
    }

    private ExchangeInfo getExchangeInfo(String exchangeName) {
        try {
            return exchangeService.getExchange(virtualHostName, exchangeName);
        } catch (Exception e) {
            log.error("amqpChannel getQueueInfo error {} ", getCurrentEnv(), e);
            return null;
        }
    }

    private String getConsumerName(String tag) {
        return getConnection().getConnectionId() + "-" + channelId + "-" + tag;
    }

    private String getCurrentEnv() {
        StringBuffer sb = new StringBuffer();
        sb.append("_amqp_env_>>");
        sb.append(connection.getConnectionId()).append("/").
            append(channelId).append("/").append(virtualHostName);
        return sb.toString();
    }


    private void processChannelCommandException(Throwable throwable) {
        Throwable exce = throwable.getCause() == null ? throwable : throwable.getCause();
        if (exce instanceof AmqpException) {
            AmqpException ex = (AmqpException) exce;
            log.error("processChannelCommandException errorCode: {} errorMessage {}", ex.getErrorCode(), ex.getMessage(), ex);
            closeChannel(ex.getErrorCode(), ex.getMessage());
        } else {
            log.error("processChannelCommandException ", exce);
            closeChannel(INTERNAL_ERROR, exce.getMessage());
        }
    }
}
