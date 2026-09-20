package cn.webank.eventmesh.client.tcp.demo;

import cn.webank.eventmesh.client.tcp.WemqAccessClient;
import cn.webank.eventmesh.client.tcp.common.AccessTestUtils;
import cn.webank.eventmesh.client.tcp.common.WemqAccessCommon;
import cn.webank.eventmesh.client.tcp.impl.DefaultWemqAccessClient;
import cn.webank.eventmesh.common.protocol.tcp.Package;
import cn.webank.eventmesh.common.protocol.tcp.UserAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncPublish{

    public static Logger logger = LoggerFactory.getLogger(AsyncPublish.class);

    private static WemqAccessClient client;

    public static AsyncPublish handler = new AsyncPublish();

    public static void main(String[] agrs)throws Exception{
        try{
            UserAgent userAgent = AccessTestUtils.generateClient1();
            client = new DefaultWemqAccessClient("127.0.0.1",10000,userAgent);
            client.init();
            client.heartbeat();

            Package asyncMsg = AccessTestUtils.asyncMessage();
            logger.info("开始发送异步单播消息：{}", asyncMsg);
            client.publish(asyncMsg, WemqAccessCommon.DEFAULT_TIME_OUT_MILLS);

            Thread.sleep(2000);
            //退出,销毁资源
//            client.close();
        }catch (Exception e){
            logger.warn("AsyncPublish failed", e);
        }
    }
}
