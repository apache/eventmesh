package org.apache.eventmesh.runtime.core.protocol.tcp.client.session.push;

import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.runtime.core.plugin.MQConsumerWrapper;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DownStreamMsgContextTest {

    private CloudEvent buildEvent() {
        return CloudEventBuilder.v1()
            .withId("test-id")
            .withSource(URI.create("test://source"))
            .withType("test-type")
            .withSubject("test-topic")
            .build();
    }

    private SubscriptionItem buildSubscriptionItem() {
        SubscriptionItem item = new SubscriptionItem();
        item.setMode(SubscriptionMode.CLUSTERING);
        return item;
    }

    @Test
    void retry_shouldNotThrowException_whenConsumerOrContextIsNull() {

        CloudEvent event = buildEvent();

        // Intentionally set to null to simulate edge case
        Session session = null;
        MQConsumerWrapper consumer = null;
        AbstractContext context = null;

        DownStreamMsgContext msgContext = new DownStreamMsgContext(
            event,
            session,
            consumer,
            context,
            false,
            buildSubscriptionItem()
        );

        assertDoesNotThrow(msgContext::retry);
    }

    @Test
    void ackMsg_shouldNotThrowException_whenDependenciesAreNull() {

        CloudEvent event = buildEvent();

        DownStreamMsgContext msgContext = new DownStreamMsgContext(
            event,
            null,
            null,
            null,
            false,
            buildSubscriptionItem()
        );

        assertDoesNotThrow(msgContext::ackMsg);
    }
}
