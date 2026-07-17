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

package org.apache.eventmesh.runtime.transport.tcp;

import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.runtime.delivery.AckCallback;
import org.apache.eventmesh.runtime.delivery.PushChannel;

import io.cloudevents.CloudEvent;
import io.netty.channel.Channel;

import lombok.extern.slf4j.Slf4j;

/**
 * Egress side of the TCP compat bridge: when the new {@code ReliableDispatcher} delivers a CloudEvent
 * to a legacy TCP subscriber, this channel encodes it into a {@code Package} (Command
 * {@code ASYNC_MESSAGE_TO_CLIENT}) carrying the delivery id, writes it to the client's netty
 * {@link Channel}, and parks the ACK callback in {@link TcpAckRegistry} until the client's
 * {@code ASYNC_MESSAGE_TO_CLIENT_ACK} frame comes back.
 *
 * <p>So the legacy TCP push target enjoys the same at-least-once reliability (redelivery, DLQ) as
 * every other transport — the dispatcher just sees another {@link PushChannel}.</p>
 */
@Slf4j
public class NettyTcpPushChannel implements PushChannel {

    /** Header property the delivery id travels under, so the client's ACK frame can echo it. */
    public static final String HEADER_DELIVERY_ID = "deliveryId";

    private final Channel channel;
    private final CloudEventToPackageBody bodyMapper;
    private final TcpAckRegistry ackRegistry;

    public NettyTcpPushChannel(Channel channel, CloudEventToPackageBody bodyMapper, TcpAckRegistry ackRegistry) {
        this.channel = channel;
        this.bodyMapper = bodyMapper;
        this.ackRegistry = ackRegistry;
    }

    @Override
    public void deliver(String deliveryId, CloudEvent event, AckCallback callback) {
        Object body;
        try {
            body = bodyMapper.toBody(event);
        } catch (RuntimeException e) {
            log.warn("tcp egress body encode failed for delivery={}", deliveryId, e);
            callback.nack(e);
            return;
        }
        Header header = new Header(Command.ASYNC_MESSAGE_TO_CLIENT, 0, "ok", null);
        header.putProperty(HEADER_DELIVERY_ID, deliveryId);
        Package push = new Package(header, body);
        try {
            // Fire-and-forget write; reliability comes from the ACK registry (client ACKs the
            // delivery id, or the dispatcher times out and redelivers).
            channel.writeAndFlush(push);
        } catch (RuntimeException e) {
            log.warn("tcp egress write failed for delivery={}", deliveryId, e);
            callback.nack(e);
            return;
        }
        // Client ACKs asynchronously via ASYNC_MESSAGE_TO_CLIENT_ACK; the registry resolves it.
        ackRegistry.register(deliveryId, callback);
    }
}
