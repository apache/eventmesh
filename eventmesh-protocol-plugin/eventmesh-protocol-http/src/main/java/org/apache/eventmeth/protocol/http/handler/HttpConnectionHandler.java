package org.apache.eventmeth.protocol.http.handler;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.apache.eventmeth.protocol.http.utils.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

public class HttpConnectionHandler extends ChannelDuplexHandler {

    private Logger httpServerLogger = LoggerFactory.getLogger("http");

    public AtomicInteger connections = new AtomicInteger(0);

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        super.channelRegistered(ctx);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        super.channelUnregistered(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        final String remoteAddress = HttpUtils.parseChannelRemoteAddr(ctx.channel());
        int c = connections.incrementAndGet();
        if (c > 20000) {
            httpServerLogger.warn("client|http|channelActive|remoteAddress={}|msg={}", remoteAddress, "too many client(20000) connect " +
                    "this eventMesh server");
            ctx.close();
            return;
        }

        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        connections.decrementAndGet();
        final String remoteAddress = HttpUtils.parseChannelRemoteAddr(ctx.channel());
        super.channelInactive(ctx);
    }


    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state().equals(IdleState.ALL_IDLE)) {
                final String remoteAddress = HttpUtils.parseChannelRemoteAddr(ctx.channel());
                httpServerLogger.info("client|http|userEventTriggered|remoteAddress={}|msg={}", remoteAddress, evt.getClass()
                        .getName());
                ctx.close();
            }
        }

        ctx.fireUserEventTriggered(evt);
    }
}
