package org.apache.eventmesh.client.grpc;

import org.apache.eventmesh.client.grpc.config.ClientConfig;
import org.apache.eventmesh.common.IPUtil;
import org.apache.eventmesh.common.ThreadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncPublish {

    private static Logger logger = LoggerFactory.getLogger(AsyncPublish.class);

    public static void main(String[] args) throws Exception {

        String serverAddr = "127.0.0.1";
        int serverPort = 5005;

        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setServerAddr(serverAddr)
                .setServerPort(serverPort)
                .setProducerGroup("EventMeshTest-producerGroup")
                .setEnv("env")
                .setIdc("idc")
                .setIp(IPUtil.getLocalAddress())
                .setSys("1234")
                .setPid(String.valueOf(ThreadUtil.getPID()));

        Publisher publisher = new Publisher(clientConfig);

        publisher.start();
        publisher.publish("hello world grpc!!");

        publisher.publish("hello world grpc2 !!");

        publisher.stop();

    }
}
