package com.webank.eventmesh.client.tcp.demo;

import com.webank.eventmesh.client.tcp.WemqAccessClient;
import com.webank.eventmesh.client.tcp.common.AccessTestUtils;
import com.webank.eventmesh.client.tcp.common.WemqAccessCommon;
import com.webank.eventmesh.client.tcp.impl.DefaultWemqAccessClient;
import com.webank.eventmesh.common.protocol.tcp.Package;
import com.webank.eventmesh.common.protocol.tcp.UserAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyncRequest {

    public static Logger logger = LoggerFactory.getLogger(SyncRequest.class);

    private static WemqAccessClient client;

    public static void main(String[] agrs)throws Exception{
        try{
            UserAgent userAgent = AccessTestUtils.generateClient1();
            client = new DefaultWemqAccessClient("127.0.0.1",10000,userAgent);
            client.init();
            client.heartbeat();

            Package rrMsg = AccessTestUtils.syncRR();
            logger.info("begin send rr msg=================={}",rrMsg);
            Package response = client.rr(rrMsg, WemqAccessCommon.DEFAULT_TIME_OUT_MILLS);
            logger.info("receive rr reply==================={}",response);

            //退出,销毁资源
//            client.close();
        }catch (Exception e){
            logger.warn("SyncRequest failed", e);
        }
    }
}
