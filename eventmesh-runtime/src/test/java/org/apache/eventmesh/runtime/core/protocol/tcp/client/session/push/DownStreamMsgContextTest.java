/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.runtime.core.protocol.tcp.client.session.push;

import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.runtime.core.plugin.MQConsumerWrapper;

import java.net.URI;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.cloudevents.core.builder.CloudEventBuilder;

public class DownStreamMsgContextTest {

    @Test
    public void testLowercaseTtlExpiresImmediatelyWhenZero() {
        Assertions.assertTrue(newContext("0").isExpire());
    }

    @Test
    public void testMissingTtlUsesDefaultTimeout() {
        Assertions.assertFalse(newContext(null).isExpire());
    }

    @Test
    public void testInvalidTtlUsesDefaultTimeout() {
        Assertions.assertFalse(newContext("invalid").isExpire());
    }

    @Test
    public void testIntegerTtl() {
        Assertions.assertFalse(newContext(5000).isExpire());
        Assertions.assertTrue(newContext(0).isExpire());
    }

    @Test
    public void testLargeTtlDoesNotOverflowExpiration() {
        Assertions.assertFalse(newContext(Long.toString(Long.MAX_VALUE)).isExpire());
        Assertions.assertFalse(newContext(Long.MAX_VALUE).isExpire());
    }

    @Test
    public void testMalformedTtlUsesDefaultTimeout() {
        for (Object ttl : new Object[]{"9223372036854775808", "-1", -1, "1.5", 1.5}) {
            Assertions.assertFalse(newContext(ttl).isExpire());
        }
    }

    @Test
    public void testRetryHonorsIntegerTtl() {
        MQConsumerWrapper consumer = Mockito.mock(MQConsumerWrapper.class);
        AbstractContext context = Mockito.mock(AbstractContext.class);
        DownStreamMsgContext message = new DownStreamMsgContext(eventWithTtl(0).build(), null, consumer, context, false, null);
        message.retry();
        Mockito.verify(consumer).updateOffset(Mockito.anyList(), Mockito.same(context));
    }

    private DownStreamMsgContext newContext(Object ttl) {
        return new DownStreamMsgContext(eventWithTtl(ttl).build(), null, null, null, false, null);
    }

    private CloudEventBuilder eventWithTtl(Object ttl) {
        CloudEventBuilder builder = CloudEventBuilder.v1()
            .withId("message-id")
            .withType("message-type")
            .withSource(URI.create("/test"));
        if (ttl instanceof Number) {
            builder.withExtension("ttl", (Number) ttl);
        } else if (ttl instanceof String) {
            builder.withExtension("ttl", (String) ttl);
        }
        return builder;
    }
}
