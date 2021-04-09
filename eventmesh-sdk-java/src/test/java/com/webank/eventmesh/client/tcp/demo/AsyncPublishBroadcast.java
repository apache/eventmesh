package com.webank.eventmesh.client.tcp.demo;

import com.webank.eventmesh.client.tcp.EventMeshClient;
import com.webank.eventmesh.client.tcp.common.EventMeshTestUtils;
import com.webank.eventmesh.client.tcp.common.EventMeshCommon;
import com.webank.eventmesh.client.tcp.impl.DefaultEventMeshClient;
import com.webank.eventmesh.common.protocol.tcp.Package;
import com.webank.eventmesh.common.protocol.tcp.UserAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncPublishBroadcast {

    public static Logger logger = LoggerFactory.getLogger(AsyncPublishBroadcast.class);

    private static EventMeshClient client;

    public static void main(String[] agrs)throws Exception{
        try{
            UserAgent userAgent = EventMeshTestUtils.generateClient1();
            client = new DefaultEventMeshClient("127.0.0.1",10002,userAgent);
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
