package org.apache.eventmesh.tcp.demo;

import org.apache.eventmesh.client.tcp.EventMeshClient;
import org.apache.eventmesh.client.tcp.common.ReceiveMsgHook;
import org.apache.eventmesh.client.tcp.impl.DefaultEventMeshClient;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.tcp.common.EventMeshTestUtils;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyncResponse implements ReceiveMsgHook {

    public static Logger logger = LoggerFactory.getLogger(SyncResponse.class);

    private static EventMeshClient client;

    public static SyncResponse handler = new SyncResponse();

    public static void main(String[] agrs)throws Exception{
        try{
            UserAgent userAgent = EventMeshTestUtils.generateClient2();
            client = new DefaultEventMeshClient("127.0.0.1",10000,userAgent);
            client.init();
            client.heartbeat();

            client.subscribe("FT0-s-80000000-01-0");
            //同步RR消息
            client.registerSubBusiHandler(handler);

            client.listen();

            //client.unsubscribe();

            //退出,销毁资源
//            client.close();
        }catch (Exception e){
            logger.warn("SyncResponse failed", e);
        }
    }

    @Override
    public void handle(Package msg, ChannelHandlerContext ctx) {
        logger.info("receive sync rr msg================{}", msg);
        Package pkg = EventMeshTestUtils.rrResponse(msg);
        ctx.writeAndFlush(pkg);
    }
}
