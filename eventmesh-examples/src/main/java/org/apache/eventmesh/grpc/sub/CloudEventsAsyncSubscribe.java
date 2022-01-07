package org.apache.eventmesh.grpc.sub;

import io.cloudevents.CloudEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.client.grpc.EventMeshGrpcConsumer;
import org.apache.eventmesh.client.grpc.ReceiveMsgHook;
import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription.SubscriptionItem;
import org.apache.eventmesh.util.Utils;

import java.util.Optional;
import java.util.Properties;

@Slf4j
public class CloudEventsAsyncSubscribe implements ReceiveMsgHook<CloudEvent> {

    public static CloudEventsAsyncSubscribe handler = new CloudEventsAsyncSubscribe();

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
            .setMode(SubscriptionItem.SubscriptionMode.CLUSTERING)
            .setType(SubscriptionItem.SubscriptionType.ASYNC)
            .build();

        Subscription subscription = Subscription.newBuilder()
            .addSubscriptionItems(subscriptionItem)
            .build();


        EventMeshGrpcConsumer eventMeshGrpcConsumer = new EventMeshGrpcConsumer(eventMeshClientConfig);

        eventMeshGrpcConsumer.init();

        eventMeshGrpcConsumer.registerListener(handler);

        eventMeshGrpcConsumer.subscribeStream(subscription);


        //eventMeshGrpcConsumer.unsubscribe(subscription);
    }

    @Override
    public Optional<CloudEvent> handle(CloudEvent msg) {
        log.info("receive async msg====================={}", msg);
        return Optional.empty();
    }

    @Override
    public String getProtocolType() {
        return EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME;
    }
}
