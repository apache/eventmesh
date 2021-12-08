package org.apache.eventmesh.client.tcp.impl;

import org.apache.eventmesh.client.tcp.EventMeshTCPClient;
import org.apache.eventmesh.client.tcp.EventMeshTCPClientFactory;
import org.apache.eventmesh.client.tcp.conf.EventMeshTCPClientConfig;
import org.apache.eventmesh.client.tcp.impl.cloudevent.CloudEventTCPClient;
import org.apache.eventmesh.client.tcp.impl.eventmeshmessage.EventMeshMessageTCPClient;
import org.apache.eventmesh.client.tcp.impl.openmessage.OpenMessageTCPClient;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;

import org.junit.Assert;
import org.junit.Test;

import io.cloudevents.CloudEvent;
import io.openmessaging.api.Message;

public class EventMeshTCPClientFactoryTest {

    @Test
    public void createEventMeshTCPClient() {
        EventMeshTCPClientConfig meshTCPClientConfig = EventMeshTCPClientConfig.builder()
            .host("localhost")
            .port(1234)
            .build();
        EventMeshTCPClient<EventMeshMessage> eventMeshMessageTCPClient =
            EventMeshTCPClientFactory.createEventMeshTCPClient(meshTCPClientConfig, EventMeshMessage.class);
        Assert.assertEquals(EventMeshMessageTCPClient.class, eventMeshMessageTCPClient.getClass());

        EventMeshTCPClient<CloudEvent> cloudEventTCPClient =
            EventMeshTCPClientFactory.createEventMeshTCPClient(meshTCPClientConfig, CloudEvent.class);
        Assert.assertEquals(CloudEventTCPClient.class, cloudEventTCPClient.getClass());

        EventMeshTCPClient<Message> openMessageTCPClient =
            EventMeshTCPClientFactory.createEventMeshTCPClient(meshTCPClientConfig, Message.class);
        Assert.assertEquals(OpenMessageTCPClient.class, openMessageTCPClient.getClass());
    }
}