package org.apache.eventmesh.eventmesh.tcp.demo;

import com.webank.eventmesh.client.tcp.EventMeshClient;
import com.webank.eventmesh.client.tcp.common.EventMeshCommon;
import com.webank.eventmesh.client.tcp.impl.DefaultEventMeshClient;
import com.webank.eventmesh.common.protocol.tcp.Package;
import com.webank.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.eventmesh.tcp.common.EventMeshTestUtils;
import org.apache.eventmesh.eventmesh.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class AsyncPublishBroadcast {

    public static Logger logger = LoggerFactory.getLogger(AsyncPublishBroadcast.class);

    private static EventMeshClient client;

    public static void main(String[] agrs)throws Exception{
        Properties properties = Utils.readPropertiesFile("application.properties");
        final String eventMeshIp = properties.getProperty("eventmesh.ip");
        final int eventMeshTcpPort = Integer.parseInt(properties.getProperty("eventmesh.tcp.port"));
        try{
            UserAgent userAgent = EventMeshTestUtils.generateClient1();
            client = new DefaultEventMeshClient(eventMeshIp,eventMeshTcpPort,userAgent);
            client.init();
            client.heartbeat();

            Package broadcastMsg = EventMeshTestUtils.broadcastMessage();
            logger.info("begin send broadcast msg============={}", broadcastMsg);
            client.broadcast(broadcastMsg, EventMeshCommon.DEFAULT_TIME_OUT_MILLS);

            Thread.sleep(2000);
            //退出,销毁资源
//            client.close();
        }catch (Exception e){
            logger.warn("AsyncPublishBroadcast failed", e);
        }
    }
}
