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

package org.apache.eventmesh.runtime.tcp.internal;

import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.runtime.delivery.AckCallback;
import org.apache.eventmesh.runtime.delivery.PushChannel;
import org.apache.eventmesh.runtime.tcp.TcpAckRegistry;

import lombok.extern.slf4j.Slf4j;

/**
 * A legacy TCP client session as a {@link PushChannel} (egress side of the compatibility bridge).
 *
 * <p>When the new {@code ReliableDispatcher} picks a TCP subscriber as a target, it hands the
 * {@link EventMeshFrame} here (the internal wire unit — no CloudEvent intermediary since #5299);
 * this channel encodes it into the legacy {@code Package} wire format (via {@link TcpFrameCodec}),
 * writes it to the socket ({@code TcpSessionSink}), and parks the ACK callback in
 * {@link TcpAckRegistry} until the client's ACK frame arrives. Reliability (redelivery, DLQ) is
 * therefore shared with every other transport — the TCP client just looks like another push target
 * to the core.</p>
 */
@Slf4j
public class TcpPushChannel implements PushChannel {

    /** Minimal write-to-socket primitive; production wraps a netty {@code Channel}. */
    @FunctionalInterface
    public interface TcpSessionSink {

        void write(byte[] frame);
    }

    private final TcpFrameCodec codec;
    private final TcpSessionSink sink;
    private final TcpAckRegistry ackRegistry;

    public TcpPushChannel(TcpFrameCodec codec, TcpSessionSink sink, TcpAckRegistry ackRegistry) {
        this.codec = codec;
        this.sink = sink;
        this.ackRegistry = ackRegistry;
    }

    @Override
    public void deliver(String deliveryId, EventMeshFrame event, AckCallback callback) {
        // Egress boundary: encode the internal Frame straight onto the legacy TCP wire format.
        byte[] frame;
        try {
            frame = codec.encodePush(deliveryId, event);
        } catch (RuntimeException e) {
            log.warn("tcp push frame->wire encode failed for delivery={}", deliveryId, e);
            callback.nack(e);
            return;
        }
        try {
            sink.write(frame);
            // The client ACKs asynchronously; the bridge resolves the callback from that frame.
            ackRegistry.register(deliveryId, callback);
        } catch (RuntimeException e) {
            // Socket write failed (broken session) → nack so the dispatcher retries on a fresh delivery.
            log.warn("tcp socket write failed for delivery={}", deliveryId, e);
            callback.nack(e);
        }
    }
}
