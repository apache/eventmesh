package org.apache.eventmesh.client.tcp.impl.eventmeshmessage;

import org.apache.eventmesh.client.tcp.EventMeshTCPPubClient;
import org.apache.eventmesh.client.tcp.common.AsyncRRCallback;
import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.client.tcp.common.MessageUtils;
import org.apache.eventmesh.client.tcp.common.PropertyConst;
import org.apache.eventmesh.client.tcp.common.ReceiveMsgHook;
import org.apache.eventmesh.client.tcp.common.RequestContext;
import org.apache.eventmesh.client.tcp.common.TcpClient;
import org.apache.eventmesh.client.tcp.conf.EventMeshTcpClientConfig;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * EventMeshMessage TCP publish client implementation.
 */
@Slf4j
public class EventMeshMessageTCPPubClient extends TcpClient implements EventMeshTCPPubClient<EventMeshMessage> {

    private final UserAgent userAgent;

    private ReceiveMsgHook<EventMeshMessage> callback;

    private final ConcurrentHashMap<String, AsyncRRCallback> callbackConcurrentHashMap = new ConcurrentHashMap<>();
    private       ScheduledFuture<?>                         task;

    public EventMeshMessageTCPPubClient(EventMeshTcpClientConfig eventMeshTcpClientConfig) {
        super(eventMeshTcpClientConfig);
        this.userAgent = eventMeshTcpClientConfig.getUserAgent();
    }

    @Override
    public void init() throws EventMeshException {
        try {
            open(new Handler());
            hello();
        } catch (Exception ex) {
            throw new EventMeshException("Initialize EventMeshMessageTCPPubClient error", ex);
        }

    }

    @Override
    public void heartbeat() throws EventMeshException {
        if (task != null) {
            synchronized (EventMeshMessageTCPPubClient.class) {
                task = scheduler.scheduleAtFixedRate(() -> {
                    try {
                        if (!isActive()) {
                            reconnect();
                        }
                        Package msg = MessageUtils.heartBeat();
                        io(msg, EventMeshCommon.DEFAULT_TIME_OUT_MILLS);
                    } catch (Exception ignore) {
                        // ignore
                    }
                }, EventMeshCommon.HEARTBEAT, EventMeshCommon.HEARTBEAT, TimeUnit.MILLISECONDS);
            }
        }
    }

    @Override
    public void reconnect() throws EventMeshException {
        try {
            super.reconnect();
            hello();
        } catch (Exception ex) {
            throw new EventMeshException("reconnect error", ex);
        }
    }

    @Override
    public Package rr(EventMeshMessage eventMeshMessage, long timeout) throws EventMeshException {
        try {
            Package msg = MessageUtils.buildPackage(eventMeshMessage, Command.REQUEST_TO_SERVER);
            log.info("{}|rr|send|type={}|msg={}", clientNo, msg, msg);
            return io(msg, timeout);
        } catch (Exception ex) {
            throw new EventMeshException("rr error");
        }
    }

    @Override
    public void asyncRR(EventMeshMessage eventMeshMessage, AsyncRRCallback callback, long timeout) throws EventMeshException {
        try {
            Package msg = MessageUtils.buildPackage(eventMeshMessage, Command.REQUEST_TO_SERVER);
            super.send(msg);
            this.callbackConcurrentHashMap.put((String) RequestContext._key(msg), callback);
        } catch (Exception ex) {
            // should trigger callback?
            throw new EventMeshException("asyncRR error", ex);
        }
    }

    @Override
    public Package publish(EventMeshMessage eventMeshMessage, long timeout) throws EventMeshException {
        try {
            // todo: transform EventMeshMessage to Package
            Package msg = MessageUtils.buildPackage(eventMeshMessage, Command.ASYNC_MESSAGE_TO_SERVER);
            log.info("SimplePubClientImpl cloud event|{}|publish|send|type={}|protocol={}|msg={}",
                clientNo, msg.getHeader().getCommand(),
                msg.getHeader().getProperty(PropertyConst.PROPERTY_MESSAGE_PROTOCOL), msg);
            return io(msg, timeout);
        } catch (Exception ex) {
            throw new EventMeshException("publish error", ex);
        }
    }

    @Override
    public void broadcast(EventMeshMessage eventMeshMessage, long timeout) throws EventMeshException {
        try {
            // todo: transform EventMeshMessage to Package
            Package msg = MessageUtils.buildPackage(eventMeshMessage, Command.BROADCAST_MESSAGE_TO_SERVER);
            log.info("{}|publish|send|type={}|protocol={}|msg={}", clientNo, msg.getHeader().getCommand(),
                msg.getHeader().getProperty(PropertyConst.PROPERTY_MESSAGE_PROTOCOL), msg);
            super.send(msg);
        } catch (Exception ex) {
            throw new EventMeshException("Broadcast message error", ex);
        }
    }

    @Override
    public void registerBusiHandler(ReceiveMsgHook<EventMeshMessage> receiveMsgHook) throws EventMeshException {
        this.callback = receiveMsgHook;
    }

    @Override
    public void close() {
        try {
            task.cancel(false);
            goodbye();
            super.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // todo: move to abstract class
    @ChannelHandler.Sharable
    private class Handler extends SimpleChannelInboundHandler<Package> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Package msg) throws Exception {
            log.info("SimplePubClientImpl|{}|receive|type={}|msg={}", clientNo, msg.getHeader(), msg);

            Command cmd = msg.getHeader().getCommand();
            if (cmd == Command.RESPONSE_TO_CLIENT) {
                if (callback != null) {
                    callback.handle(msg, ctx);
                }
                Package pkg = MessageUtils.responseToClientAck(msg);
                send(pkg);
            } else if (cmd == Command.SERVER_GOODBYE_REQUEST) {
                //TODO
            }

            RequestContext context = contexts.get(RequestContext._key(msg));
            if (context != null) {
                contexts.remove(context.getKey());
                context.finish(msg);
            }
        }
    }

    // todo: remove hello
    private void hello() throws Exception {
        Package msg = MessageUtils.hello(userAgent);
        this.io(msg, EventMeshCommon.DEFAULT_TIME_OUT_MILLS);
    }

    // todo: remove goodbye
    private void goodbye() throws Exception {
        Package msg = MessageUtils.goodbye();
        this.io(msg, EventMeshCommon.DEFAULT_TIME_OUT_MILLS);
    }
}
