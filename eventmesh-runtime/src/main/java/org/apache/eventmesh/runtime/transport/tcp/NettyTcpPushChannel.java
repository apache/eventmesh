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
import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.protocol.api.FrameAdaptors;
import org.apache.eventmesh.runtime.delivery.AckCallback;
import org.apache.eventmesh.runtime.delivery.PushChannel;

import io.netty.channel.Channel;

import lombok.extern.slf4j.Slf4j;

/**
 * Egress side of the TCP compat bridge: converts an {@link EventMeshFrame} directly to a MeshMessage
 * {@code Package} (Command {@code ASYNC_MESSAGE_TO_CLIENT}) via {@link MeshMessageFrameCodec} — no
 * CloudEvent intermediary (the legacy SDK only speaks MeshMessage). Carries the delivery id, writes
 * to the client's netty {@link Channel}, parks the ACK callback in {@link TcpAckRegistry} until the
 * client's {@code ASYNC_MESSAGE_TO_CLIENT_ACK} frame arrives.
 *
 * <p>So the legacy TCP push target enjoys the same at-least-once reliability (redelivery, DLQ) as
 * every other transport — the dispatcher just sees another {@link PushChannel}.</p>
 */
@Slf4j
public class NettyTcpPushChannel implements PushChannel {

    /** Header property the delivery id travels under, so the client's ACK frame can echo it. */
    public static final String HEADER_DELIVERY_ID = "deliveryId";

    private final Channel channel;
    private final TcpAckRegistry ackRegistry;

    public NettyTcpPushChannel(Channel channel, TcpAckRegistry ackRegistry) {
        this.channel = channel;
        this.ackRegistry = ackRegistry;
    }

    @Override
    public void deliver(String deliveryId, EventMeshFrame event, AckCallback callback) {
        // Egress: Frame → MeshMessage Package directly (no CloudEvent).
        Package push;
        try {
            push = (Package) FrameAdaptors.get("meshmessage").fromFrameSilent(event);
            push.setHeader(new Header(Command.ASYNC_MESSAGE_TO_CLIENT, 0, "ok", null));
            push.getHeader().putProperty(HEADER_DELIVERY_ID, deliveryId);
        } catch (RuntimeException e) {
            log.warn("tcp egress frame->MeshMessage encode failed for delivery={}", deliveryId, e);
            callback.nack(e);
            return;
        }
        try {
            channel.writeAndFlush(push);
        } catch (RuntimeException e) {
            log.warn("tcp egress write failed for delivery={}", deliveryId, e);
            callback.nack(e);
            return;
        }
        ackRegistry.register(deliveryId, callback);
    }
}
