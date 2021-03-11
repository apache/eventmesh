package com.webank.eventmesh.tcp.demo;

import com.webank.eventmesh.client.tcp.WemqAccessClient;
import com.webank.eventmesh.client.tcp.common.WemqAccessCommon;
import com.webank.eventmesh.client.tcp.impl.DefaultWemqAccessClient;
import com.webank.eventmesh.common.protocol.tcp.Package;
import com.webank.eventmesh.common.protocol.tcp.UserAgent;
import com.webank.eventmesh.tcp.common.AccessTestUtils;
import com.webank.eventmesh.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class AsyncPublishBroadcast {

    public static Logger logger = LoggerFactory.getLogger(AsyncPublishBroadcast.class);

    private static WemqAccessClient client;

    public static void main(String[] agrs)throws Exception{
        Properties properties = Utils.readPropertiesFile("application.properties");
        final String eventMeshIp = properties.getProperty("eventmesh.ip");
        final int eventMeshTcpPort = Integer.parseInt(properties.getProperty("eventmesh.tcp.port"));
        try{
            UserAgent userAgent = AccessTestUtils.generateClient1();
            client = new DefaultWemqAccessClient(eventMeshIp,eventMeshTcpPort,userAgent);
            client.init();
            client.heartbeat();

            Package broadcastMsg = AccessTestUtils.broadcastMessage();
            logger.info("begin send broadcast msg============={}", broadcastMsg);
            client.broadcast(broadcastMsg, WemqAccessCommon.DEFAULT_TIME_OUT_MILLS);

            Thread.sleep(2000);
            //退出,销毁资源
//            client.close();
        }catch (Exception e){
            logger.warn("AsyncPublishBroadcast failed", e);
        }
    }
}
