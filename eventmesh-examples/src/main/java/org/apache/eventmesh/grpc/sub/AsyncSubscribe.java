package org.apache.eventmesh.grpc.sub;

import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.client.grpc.EventMeshGrpcConsumer;
import org.apache.eventmesh.client.grpc.ReceiveMsgHook;
import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription.SubscriptionItem;
import org.apache.eventmesh.util.Utils;

import java.util.Optional;
import java.util.Properties;

@Slf4j
public class AsyncSubscribe implements ReceiveMsgHook<EventMeshMessage> {

    public static AsyncSubscribe handler = new AsyncSubscribe();

    public static void main(String[] args) {
        Properties properties = Utils.readPropertiesFile("application.properties");
        final String eventMeshIp = properties.getProperty("eventmesh.ip");
        final String eventMeshGrpcPort = properties.getProperty("eventmesh.grpc.port");

        final String topic = "TEST-TOPIC-GRPC-ASYNC";

        EventMeshGrpcClientConfig eventMeshClientConfig = EventMeshGrpcClientConfig.builder()
            .serverAddr(eventMeshIp)
            .serverPort(Integer.parseInt(eventMeshGrpcPort))
            .consumerGroup("EventMeshTest-consumerGroup")
            .env("env").idc("idc")
            .sys("1234").build();

        SubscriptionItem subscriptionItem = SubscriptionItem.newBuilder()
            .setTopic(topic)
            .setMode(Subscription.SubscriptionItem.SubscriptionMode.CLUSTERING)
            .setType(Subscription.SubscriptionItem.SubscriptionType.ASYNC)
            .build();

        Subscription subscription = Subscription.newBuilder()
            .addSubscriptionItems(subscriptionItem)
            .build();


        EventMeshGrpcConsumer eventMeshGrpcConsumer = new EventMeshGrpcConsumer(eventMeshClientConfig);

        eventMeshGrpcConsumer.init();

        eventMeshGrpcConsumer.registerListener(handler);

        eventMeshGrpcConsumer.subscribeStream(subscription);

    }

    @Override
    public Optional<EventMeshMessage> handle(EventMeshMessage msg) {
        log.info("receive async msg====================={}", msg);
        return Optional.empty();
    }
}
