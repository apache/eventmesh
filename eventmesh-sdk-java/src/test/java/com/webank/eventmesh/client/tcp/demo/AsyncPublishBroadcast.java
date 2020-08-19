package com.webank.eventmesh.client.tcp.demo;

import com.webank.eventmesh.client.tcp.WemqAccessClient;
import com.webank.eventmesh.client.tcp.common.AccessTestUtils;
import com.webank.eventmesh.client.tcp.common.WemqAccessCommon;
import com.webank.eventmesh.client.tcp.impl.DefaultWemqAccessClient;
import com.webank.eventmesh.common.protocol.tcp.Package;
import com.webank.eventmesh.common.protocol.tcp.UserAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncPublishBroadcast {

    public static Logger logger = LoggerFactory.getLogger(AsyncPublishBroadcast.class);

    private static WemqAccessClient client;

    public static void main(String[] agrs)throws Exception{
        try{
            UserAgent userAgent = AccessTestUtils.generateClient1();
            client = new DefaultWemqAccessClient("127.0.0.1",10000,userAgent);
            client.init();
            client.heartbeat();

            Package broadcastMsg = AccessTestUtils.broadcastMessage();
            logger.info("开始发送广播消息：{}", broadcastMsg);
            client.broadcast(broadcastMsg, WemqAccessCommon.DEFAULT_TIME_OUT_MILLS);

            Thread.sleep(2000);
            //退出,销毁资源
//            client.close();
        }catch (Exception e){
            logger.warn("AsyncPublishBroadcast failed", e);
        }
    }
}
