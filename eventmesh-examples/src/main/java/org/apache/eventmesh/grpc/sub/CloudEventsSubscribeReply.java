package org.apache.eventmesh.grpc.sub;

import io.cloudevents.CloudEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.client.grpc.consumer.EventMeshGrpcConsumer;
import org.apache.eventmesh.client.grpc.consumer.ReceiveMsgHook;
import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.util.Utils;

import java.util.Collections;
import java.util.Optional;
import java.util.Properties;

@Slf4j
public class CloudEventsSubscribeReply implements ReceiveMsgHook<CloudEvent> {

    public static CloudEventsSubscribeReply handler = new CloudEventsSubscribeReply();

    public static void main(String[] args) throws InterruptedException {
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

        SubscriptionItem subscriptionItem = new SubscriptionItem();
        subscriptionItem.setTopic(topic);
        subscriptionItem.setMode(SubscriptionMode.CLUSTERING);
        subscriptionItem.setType(SubscriptionType.ASYNC);


        EventMeshGrpcConsumer eventMeshGrpcConsumer = new EventMeshGrpcConsumer(eventMeshClientConfig);

        eventMeshGrpcConsumer.init();

        eventMeshGrpcConsumer.registerListener(handler);

        eventMeshGrpcConsumer.subscribe(Collections.singletonList(subscriptionItem));

        Thread.sleep(60000);
        eventMeshGrpcConsumer.unsubscribe(Collections.singletonList(subscriptionItem));
    }

    @Override
    public Optional<CloudEvent> handle(CloudEvent msg) {
        log.info("receive async msg====================={}", msg);
        return Optional.of(msg);
    }

    @Override
    public String getProtocolType() {
        return EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME;
    }
}
