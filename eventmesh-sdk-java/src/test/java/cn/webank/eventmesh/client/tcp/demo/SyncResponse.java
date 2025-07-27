package cn.webank.eventmesh.client.tcp.demo;

import cn.webank.eventmesh.client.tcp.WemqAccessClient;
import cn.webank.eventmesh.client.tcp.common.AccessTestUtils;
import cn.webank.eventmesh.client.tcp.common.ReceiveMsgHook;
import cn.webank.eventmesh.client.tcp.impl.DefaultWemqAccessClient;
import cn.webank.eventmesh.common.protocol.tcp.Package;
import cn.webank.eventmesh.common.protocol.tcp.UserAgent;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyncResponse implements ReceiveMsgHook{

    public static Logger logger = LoggerFactory.getLogger(SyncResponse.class);

    private static WemqAccessClient client;

    public static SyncResponse handler = new SyncResponse();

    public static void main(String[] agrs)throws Exception{
        try{
            UserAgent userAgent = AccessTestUtils.generateClient2();
            client = new DefaultWemqAccessClient("127.0.0.1",10000,userAgent);
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
        logger.info("sub收到消息：{}", msg);
        Package pkg = AccessTestUtils.rrResponse(msg);
        ctx.writeAndFlush(pkg);
    }
}
