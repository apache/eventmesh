package org.apache.eventmesh.tcp.demo;

import org.apache.eventmesh.client.tcp.EventMeshClient;
import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.client.tcp.impl.DefaultEventMeshClient;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.tcp.common.EventMeshTestUtils;
import org.apache.eventmesh.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class AsyncPublish{

    public static Logger logger = LoggerFactory.getLogger(AsyncPublish.class);

    private static EventMeshClient client;

    public static AsyncPublish handler = new AsyncPublish();


    public static void main(String[] agrs)throws Exception{
        Properties properties = Utils.readPropertiesFile("application.properties");
        final String eventMeshIp = properties.getProperty("eventmesh.ip");
        final int eventMeshTcpPort = Integer.parseInt(properties.getProperty("eventmesh.tcp.port"));
        try{
            UserAgent userAgent = EventMeshTestUtils.generateClient1();
            client = new DefaultEventMeshClient(eventMeshIp,eventMeshTcpPort,userAgent);
            client.init();
            client.heartbeat();

            for(int i=0; i < 5; i++) {
                Package asyncMsg = EventMeshTestUtils.asyncMessage();
                logger.info("begin send async msg[{}]==================={}", i, asyncMsg);
                client.publish(asyncMsg, EventMeshCommon.DEFAULT_TIME_OUT_MILLS);

                Thread.sleep(1000);
            }

            Thread.sleep(2000);
            //退出,销毁资源
//            client.close();
        }catch (Exception e){
            logger.warn("AsyncPublish failed", e);
        }
    }
}
