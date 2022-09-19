package org.apache.eventmesh.connector.pulsar.utils;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import org.apache.eventmesh.api.SendResult;
import org.junit.Assert;
import org.junit.Test;

import java.net.URI;
import java.time.OffsetDateTime;

public class CloudEventUtilsTest {

    @Test
    public void testConvertSendResult() {
        CloudEvent cloudEvent = CloudEventBuilder.v1()
            .withId("001")
            .withTime(OffsetDateTime.now())
            .withSource(URI.create("testsource"))
            .withSubject("testTopic")
            .withType(String.class.getCanonicalName())
            .withDataContentType("text/plain")
            .withData("data".getBytes())
            .build();

       SendResult result = CloudEventUtils.convertSendResult(cloudEvent);
       Assert.assertEquals("testTopic",result.getTopic());
       Assert.assertEquals("001",result.getMessageId());
    }

}
