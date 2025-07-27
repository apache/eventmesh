package org.apache.eventmesh.tcp.demo;

import com.webank.eventmesh.client.tcp.EventMeshClient;
import com.webank.eventmesh.client.tcp.common.EventMeshCommon;
import com.webank.eventmesh.client.tcp.impl.DefaultEventMeshClient;
import com.webank.eventmesh.common.protocol.tcp.Package;
import com.webank.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.tcp.common.EventMeshTestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyncRequest {

    public static Logger logger = LoggerFactory.getLogger(SyncRequest.class);

    private static EventMeshClient client;

    public static void main(String[] agrs)throws Exception{
        try{
            UserAgent userAgent = EventMeshTestUtils.generateClient1();
            client = new DefaultEventMeshClient("127.0.0.1",10000,userAgent);
            client.init();
            client.heartbeat();

            Package rrMsg = EventMeshTestUtils.syncRR();
            logger.info("begin send rr msg=================={}",rrMsg);
            Package response = client.rr(rrMsg, EventMeshCommon.DEFAULT_TIME_OUT_MILLS);
            logger.info("receive rr reply==================={}",response);

            //退出,销毁资源
//            client.close();
        }catch (Exception e){
            logger.warn("SyncRequest failed", e);
        }
    }
}
