package org.apache.eventmesh.client.tcp.impl;

import org.apache.eventmesh.client.tcp.common.MessageUtils;
import org.apache.eventmesh.client.tcp.common.RequestContext;
import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.Package;

import java.util.concurrent.ConcurrentHashMap;

import com.google.common.base.Preconditions;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractEventMeshTCPPubHandler<ProtocolMessage> extends SimpleChannelInboundHandler<Package> {

    private final ConcurrentHashMap<Object, RequestContext> contexts;

    public AbstractEventMeshTCPPubHandler(ConcurrentHashMap<Object, RequestContext> contexts) {
        this.contexts = contexts;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Package msg) throws Exception {
        log.info("SimplePubClientImpl|receive|msg={}", msg);

        Preconditions.checkNotNull(msg.getHeader(), "Tcp package header cannot be null");
        Command cmd = msg.getHeader().getCmd();
        switch (cmd) {
            case RESPONSE_TO_CLIENT:
                callback(getMessage(msg), ctx);
                sendResponse(MessageUtils.responseToClientAck(msg));
                break;
            case SERVER_GOODBYE_REQUEST:
                //TODO
                break;
            default:
                break;

        }
        RequestContext context = contexts.get(RequestContext._key(msg));
        if (context != null) {
            contexts.remove(context.getKey());
            context.finish(msg);
        }
    }

    public abstract void callback(ProtocolMessage protocolMessage, ChannelHandlerContext ctx);

    public abstract ProtocolMessage getMessage(Package tcpPackage);

    public abstract void sendResponse(Package tcpPackage);

}
