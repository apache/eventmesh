package org.apache.eventmesh.client.tcp.demo;

import org.apache.eventmesh.client.tcp.EventMeshClient;
import org.apache.eventmesh.client.tcp.common.EventMeshTestUtils;
import org.apache.eventmesh.client.tcp.common.ReceiveMsgHook;
import org.apache.eventmesh.client.tcp.impl.DefaultEventMeshClient;
import com.webank.eventmesh.common.protocol.tcp.EventMeshMessage;
import com.webank.eventmesh.common.protocol.tcp.Package;
import com.webank.eventmesh.common.protocol.tcp.UserAgent;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncSubscribeBroadcast implements ReceiveMsgHook {

    public static Logger logger = LoggerFactory.getLogger(AsyncSubscribeBroadcast.class);

    private static EventMeshClient client;

    public static AsyncSubscribeBroadcast handler = new AsyncSubscribeBroadcast();

    public static void main(String[] agrs)throws Exception{
        try{
            UserAgent userAgent = EventMeshTestUtils.generateClient2();
            client = new DefaultEventMeshClient("127.0.0.1",10002,userAgent);
            client.init();
            client.heartbeat();

            client.subscribe("FT0-e-80030001-01-3");
            client.registerSubBusiHandler(handler);

            client.listen();

            //client.unsubscribe();

            //退出,销毁资源
//            client.close();
        }catch (Exception e){
            logger.warn("AsyncSubscribeBroadcast failed", e);
        }
    }

    @Override
    public void handle(Package msg, ChannelHandlerContext ctx) {
        EventMeshMessage eventMeshMessage = (EventMeshMessage)msg.getBody();
        logger.info("receive broadcast msg==============={}", eventMeshMessage);
    }
}
