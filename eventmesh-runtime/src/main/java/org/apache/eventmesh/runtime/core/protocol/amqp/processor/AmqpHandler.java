/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.runtime.core.protocol.amqp.processor;

import org.apache.eventmesh.runtime.boot.EventMeshAmqpServer;
import org.apache.eventmesh.runtime.core.protocol.amqp.exception.AmqpFrameDecodingException;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.AMQPFrame;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.protocol.ProtocolFrame;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.UnknownClassOrMethodId;
import com.rabbitmq.client.impl.AMQImpl;
import com.rabbitmq.client.impl.Method;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AmqpHandler extends ChannelInboundHandlerAdapter implements ConnectionMethodProcessor {

    protected ChannelHandlerContext ctx;
    protected SocketAddress remoteAddress;
    // TODO
    protected final EventMeshAmqpServer amqpServer;
    @Getter
    protected AtomicBoolean isActive = new AtomicBoolean(false);

    protected AmqpHandler(EventMeshAmqpServer amqpServer) {
        this.amqpServer = amqpServer;
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("Channel writability has changed to: {}", ctx.channel().isWritable());
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof List) {
            for (final Object m : (List<?>) msg) {
                AmqpHandler.this.process(ctx, m);
            }
        } else {
            process(ctx, msg);
        }

    }

    private void process(ChannelHandlerContext ctx, Object msg) throws AmqpFrameDecodingException {
        if (msg instanceof ProtocolFrame) {
            receiveProtocolHeader((ProtocolFrame) msg);
        } else if (msg instanceof AMQPFrame) {
            processFrame((AMQPFrame) msg);
        }

    }

    private void processFrame(AMQPFrame frame) throws AmqpFrameDecodingException {
        try {
            int channelNo = frame.getChannel();
            switch (frame.getType()) {
                case AMQP.FRAME_METHOD:
                    try {
                        Method method = AMQImpl.readMethodFrom(frame.getInputStream());
                        processMethod(channelNo, method);
                    } catch (UnknownClassOrMethodId e) {
                        log.error("method frame serializer exception  classId {} ,methodId {}", e.classId, e.methodId, e);
                    } catch (IOException e) {
                        log.error("method frame serializer exception", e);
                        //throw new AMQFrameDecodingException("method frame serializer exception: " + AMQP.FRAME_METHOD);
                    }

                    break;
                case AMQP.FRAME_HEADER:
                    getChannelMethodProcessor(channelNo).receiveMessageHeader(frame);
                    break;
                case AMQP.FRAME_BODY:
                    getChannelMethodProcessor(channelNo).receiveMessageContent(frame);
                    break;
                case AMQP.FRAME_HEARTBEAT:
                    receiveHeartbeat();
                    break;
                default:
                    throw new AmqpFrameDecodingException("Unsupported frame type: " + frame.getType());
            }
        } finally {
            // safe release??
            frame.getPayload().release();
        }
    }

    private void processMethod(int channelNo, Method method) throws UnknownClassOrMethodId {
        int classId = method.protocolClassId();
        int methodId = method.protocolMethodId();
        if (ignoreAllButCloseOk()) {
            if (!(classId == 10 && methodId == 51)) {
                return;
            }
        }
        if (classId != 10 && getChannelMethodProcessor(channelNo).ignoreAllButCloseOk()) {
            if (!(classId == 20 && methodId == 41)) {
                return;
            }
        }
        switch (classId) {
            //CONNECTION_CLASS:
            case 10:
                switch (methodId) {
                    case 11: {
                        AMQP.Connection.StartOk startOk = (AMQP.Connection.StartOk) method;
                        receiveConnectionStartOk(startOk.getClientProperties(), startOk.getMechanism(), startOk.getResponse(), startOk.getLocale());
                    }
                    break;
                    case 21: {
                        AMQP.Connection.SecureOk secureOk = (AMQP.Connection.SecureOk) method;
                        receiveConnectionSecureOk(secureOk.getResponse());
                    }
                    break;
                    case 31: {
                        AMQP.Connection.TuneOk tuneOk = (AMQP.Connection.TuneOk) method;
                        receiveConnectionTuneOk(tuneOk.getChannelMax(), tuneOk.getFrameMax(), tuneOk.getHeartbeat());
                    }
                    break;
                    case 40: {
                        AMQP.Connection.Open open = (AMQP.Connection.Open) method;
                        receiveConnectionOpen(open.getVirtualHost(), open.getCapabilities(), open.getInsist());
                    }
                    break;
                    case 50: {
                        AMQP.Connection.Close close = (AMQP.Connection.Close) method;
                        receiveConnectionClose(close.getReplyCode(), close.getReplyText(), close.getClassId(), close.getMethodId());
                    }
                    break;
                    case 51: {
                        receiveConnectionCloseOk();
                    }
                    break;
                    // TODO need impl???
//                    case 60: {
//                        return new AMQImpl.Connection.Blocked(new MethodArgumentReader(new ValueReader(in)));
//                    }
//                    case 61: {
//                        return new AMQImpl.Connection.Unblocked(new MethodArgumentReader(new ValueReader(in)));
//                    }
//                    case 70: {
//                        return new AMQImpl.Connection.UpdateSecret(new MethodArgumentReader(new ValueReader(in)));
//                    }
//                    case 71: {
//                        return new AMQImpl.Connection.UpdateSecretOk(new MethodArgumentReader(new ValueReader(in)));
//                    }
                    default:
                        break;
                }
                break;
            case 20:
                switch (methodId) {
                    case 10: {
                        receiveChannelOpen(channelNo);
                    }
                    break;
                    case 20: {
                        AMQP.Channel.Flow flow = (AMQP.Channel.Flow) method;
                        getChannelMethodProcessor(channelNo).receiveChannelFlow(flow.getActive());
                    }
                    break;
                    case 21: {
                        AMQP.Channel.FlowOk flowOk = (AMQP.Channel.FlowOk) method;
                        getChannelMethodProcessor(channelNo).receiveChannelFlowOk(flowOk.getActive());
                    }
                    break;
                    case 40: {
                        AMQP.Channel.Close close = (AMQP.Channel.Close) method;
                        getChannelMethodProcessor(channelNo).receiveChannelClose(close.getReplyCode(),
                            close.getReplyText(), close.getClassId(), close.getMethodId());
                    }
                    break;
                    case 41: {
                        getChannelMethodProcessor(channelNo).receiveChannelCloseOk();
                    }
                    break;
                    default:
                        break;
                }
                break;
            case 30:
                switch (methodId) {
                    case 10: {
                        AMQP.Access.Request request = (AMQP.Access.Request) method;
                        getChannelMethodProcessor(channelNo).receiveAccessRequest(request.getRealm(),
                            request.getExclusive(), request.getPassive(), request.getActive(), request.getWrite(),
                            request.getRead());
                    }
                    break;

                    default:
                        break;
                }
                break;
            case 40:
                switch (methodId) {
                    case 10: {
                        AMQP.Exchange.Declare declare = (AMQP.Exchange.Declare) method;
                        getChannelMethodProcessor(channelNo).receiveExchangeDeclare(declare.getExchange(),
                            declare.getType(), declare.getPassive(), declare.getDurable(), declare.getAutoDelete(),
                            declare.getInternal(), declare.getNowait(), declare.getArguments());
                    }
                    break;
                    case 20: {
                        AMQP.Exchange.Delete delete = (AMQP.Exchange.Delete) method;
                        getChannelMethodProcessor(channelNo).receiveExchangeDelete(delete.getExchange(), delete.getIfUnused(), delete.getNowait());
                    }
                    break;

                    case 30: {
                        AMQP.Exchange.Bind bind = (AMQP.Exchange.Bind) method;
                        getChannelMethodProcessor(channelNo).receiveExchangeBound(bind.getSource(), bind.getRoutingKey(), bind.getDestination());
                    }
                    break;
//                    case 40: {
//                        getChannelMethodProcessor(channelNo).receiveExchangeBound(method);
//                        return new AMQImpl.Exchange.Unbind(new MethodArgumentReader(new ValueReader(in)));
//                    }
//                    case 51: {
//                        return new AMQImpl.Exchange.UnbindOk(new MethodArgumentReader(new ValueReader(in)));
//                    }
                    default:
                        break;
                }
                break;
            case 50:
                switch (methodId) {
                    case 10: {
                        AMQP.Queue.Declare declare = (AMQP.Queue.Declare) method;
                        getChannelMethodProcessor(channelNo).receiveQueueDeclare(declare.getQueue(),
                            declare.getPassive(), declare.getDurable(), declare.getExclusive(), declare.getAutoDelete(),
                            declare.getNowait(), declare.getArguments());
                    }
                    break;
                    case 20: {
                        AMQP.Queue.Bind bind = (AMQP.Queue.Bind) method;
                        getChannelMethodProcessor(channelNo).receiveQueueBind(bind.getQueue(), bind.getExchange(),
                            bind.getRoutingKey(), bind.getNowait(), bind.getArguments());
                    }
                    break;
                    case 30: {
                        AMQP.Queue.Purge purge = (AMQP.Queue.Purge) method;
                        getChannelMethodProcessor(channelNo).receiveQueuePurge(purge.getQueue(), purge.getNowait());
                    }
                    break;
                    case 40: {
                        AMQP.Queue.Delete delete = (AMQP.Queue.Delete) method;
                        getChannelMethodProcessor(channelNo).receiveQueueDelete(delete.getQueue(), delete.getIfUnused(),
                            delete.getIfEmpty(), delete.getNowait());
                    }
                    break;
                    case 50: {
                        AMQP.Queue.Unbind unbind = (AMQP.Queue.Unbind) method;
                        getChannelMethodProcessor(channelNo).receiveQueueUnbind(unbind.getQueue(), unbind.getExchange(),
                            unbind.getRoutingKey(), unbind.getArguments());
                    }
                    break;

                    default:
                        break;
                }
                break;
            case 60:
                switch (methodId) {
                    case 10: {
                        AMQP.Basic.Qos qos = (AMQP.Basic.Qos) method;
                        getChannelMethodProcessor(channelNo).receiveBasicQos(qos.getPrefetchSize(),
                            qos.getPrefetchCount(), qos.getGlobal());
                    }
                    break;
                    case 20: {
                        AMQP.Basic.Consume consume = (AMQP.Basic.Consume) method;
                        getChannelMethodProcessor(channelNo).receiveBasicConsume(consume.getQueue(),
                            consume.getConsumerTag(), consume.getNoLocal(), consume.getNoAck(), consume.getExclusive(),
                            consume.getNowait(), consume.getArguments());
                    }
                    break;
                    case 30: {
                        AMQP.Basic.Cancel cancel = (AMQP.Basic.Cancel) method;
                        getChannelMethodProcessor(channelNo).receiveBasicCancel(cancel.getConsumerTag(), cancel.getNowait());
                    }
                    break;
                    case 40: {
                        AMQP.Basic.Publish publish = (AMQP.Basic.Publish) method;
                        getChannelMethodProcessor(channelNo).receiveBasicPublish(publish.getExchange(),
                            publish.getRoutingKey(), publish.getMandatory(), publish.getImmediate());
                    }
                    break;

                    case 70: {
                        AMQP.Basic.Get get = (AMQP.Basic.Get) method;
                        getChannelMethodProcessor(channelNo).receiveBasicGet(get.getQueue(), get.getNoAck());
                    }
                    break;

                    case 80: {
                        AMQP.Basic.Ack ack = (AMQP.Basic.Ack) method;
                        getChannelMethodProcessor(channelNo).receiveBasicAck(ack.getDeliveryTag(), ack.getMultiple());
                    }
                    break;
                    case 90: {
                        AMQP.Basic.Reject reject = (AMQP.Basic.Reject) method;
                        getChannelMethodProcessor(channelNo).receiveBasicReject(reject.getDeliveryTag(), reject.getRequeue());
                    }
                    break;
                    case 100: {
                        AMQP.Basic.RecoverAsync recover = (AMQP.Basic.RecoverAsync) method;
                        getChannelMethodProcessor(channelNo).receiveBasicRecover(recover.getRequeue(), false);
                    }
                    break;
                    case 110: {
                        AMQP.Basic.Recover recover = (AMQP.Basic.Recover) method;
                        getChannelMethodProcessor(channelNo).receiveBasicRecover(recover.getRequeue(), true);
                    }
                    break;
                    case 120: {
                        AMQP.Basic.Nack noack = (AMQP.Basic.Nack) method;
                        getChannelMethodProcessor(channelNo).receiveBasicNack(noack.getDeliveryTag(), noack.getMultiple(), noack.getRequeue());
                    }
                    break;
                    default:
                        break;
                }
                break;
            case 90:
                switch (methodId) {
                    case 10: {
                        getChannelMethodProcessor(channelNo).receiveTxSelect();
                    }
                    break;

                    case 20: {
                        getChannelMethodProcessor(channelNo).receiveTxCommit();
                    }
                    break;

                    case 30: {
                        getChannelMethodProcessor(channelNo).receiveTxRollback();
                    }
                    break;

                    default:
                        break;
                }
                break;
            case 85:
                switch (methodId) {
                    case 10: {
                        AMQP.Confirm.Select select = (AMQP.Confirm.Select) method;
                        getChannelMethodProcessor(channelNo).receiveConfirmSelect(select.getNowait());
                    }
                    break;
                    default:
                        break;
                }
                break;
        }

    }

    public abstract void close();

    public ChannelHandlerContext getCtx() {
        return ctx;
    }

}
