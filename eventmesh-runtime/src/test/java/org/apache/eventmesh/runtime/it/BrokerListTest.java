package org.apache.eventmesh.runtime.it;

import org.apache.rocketmq.remoting.netty.NettyRemotingClient;
import org.apache.rocketmq.remoting.netty.NettyClientConfig;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.common.protocol.route.TopicRouteData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "it.storage", matches = "rocketmq|kafka")
class BrokerListTest {
    @Test
    void listBrokers() throws Exception {
        String namesrv = System.getProperty("it.namesrv", "localhost:9876");
        NettyRemotingClient client = new NettyRemotingClient(new NettyClientConfig());
        client.start();
        
        // GET_BROKER_CLUSTER_INFO = 106
        RemotingCommand request = RemotingCommand.createRequestCommand(106, null);
        RemotingCommand response = client.invokeSync(namesrv, request, 5000);
        
        System.out.println("IT-BROKERS response code=" + response.getCode());
        if (response.getBody() != null) {
            String json = new String(response.getBody(), "UTF-8");
            System.out.println("IT-BROKERS cluster info: " + json.substring(0, Math.min(2000, json.length())));
        }
        
        // Also try fetching route for a test topic
        org.apache.rocketmq.common.protocol.header.namesrv.GetRouteInfoRequestHeader header =
            new org.apache.rocketmq.common.protocol.header.namesrv.GetRouteInfoRequestHeader();
        header.setTopic("TBW102"); // default topic
        RemotingCommand routeReq = RemotingCommand.createRequestCommand(105, header);
        RemotingCommand routeResp = client.invokeSync(namesrv, routeReq, 5000);
        if (routeResp.getBody() != null) {
            TopicRouteData route = org.apache.rocketmq.remoting.protocol.RemotingSerializable.decode(
                routeResp.getBody(), TopicRouteData.class);
            System.out.println("IT-BROKERS route for TBW102: " + route.getQueueDatas().size() + " queueDatas, " 
                + route.getBrokerDatas().size() + " brokerDatas");
            for (var qd : route.getQueueDatas()) {
                System.out.println("IT-BROKERS queueData: broker=" + qd.getBrokerName() 
                    + " readQ=" + qd.getReadQueueNums() + " writeQ=" + qd.getWriteQueueNums());
            }
            for (var bd : route.getBrokerDatas()) {
                System.out.println("IT-BROKERS brokerData: name=" + bd.getBrokerName() 
                    + " addrs=" + bd.getBrokerAddrs());
            }
        }
        client.shutdown();
    }
}
