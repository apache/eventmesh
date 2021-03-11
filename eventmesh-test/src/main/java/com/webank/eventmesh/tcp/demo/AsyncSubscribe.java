package com.webank.eventmesh.tcp.demo;

import com.webank.eventmesh.client.tcp.WemqAccessClient;
import com.webank.eventmesh.client.tcp.common.ReceiveMsgHook;
import com.webank.eventmesh.client.tcp.impl.DefaultWemqAccessClient;
import com.webank.eventmesh.common.protocol.tcp.AccessMessage;
import com.webank.eventmesh.common.protocol.tcp.Package;
import com.webank.eventmesh.common.protocol.tcp.UserAgent;
import com.webank.eventmesh.tcp.common.AccessTestUtils;
import com.webank.eventmesh.util.Utils;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class AsyncSubscribe implements ReceiveMsgHook {

    public static Logger logger = LoggerFactory.getLogger(AsyncSubscribe.class);

    private static WemqAccessClient client;

    public static AsyncSubscribe handler = new AsyncSubscribe();

    public static void main(String[] agrs)throws Exception{
        Properties properties = Utils.readPropertiesFile("application.properties");
        final String eventMeshIp = properties.getProperty("eventmesh.ip");
        final int eventMeshTcpPort = Integer.parseInt(properties.getProperty("eventmesh.tcp.port"));
        try{
            UserAgent userAgent = AccessTestUtils.generateClient2();
            client = new DefaultWemqAccessClient(eventMeshIp,eventMeshTcpPort,userAgent);
            client.init();
            client.heartbeat();

            client.subscribe("FT0-e-80010000-01-1");
            client.registerSubBusiHandler(handler);

            client.listen();

            //client.unsubscribe();

            //退出,销毁资源
//            client.close();
        }catch (Exception e){
            logger.warn("AsyncSubscribe failed", e);
        }
    }

    @Override
    public void handle(Package msg, ChannelHandlerContext ctx) {
        AccessMessage accessMessage = (AccessMessage)msg.getBody();
        logger.info("receive async msg====================={}", accessMessage);
    }
}
