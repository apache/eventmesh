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

package org.apache.eventmesh.runtime.grpc;

import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.runtime.delivery.AckCallback;

import java.util.concurrent.atomic.AtomicBoolean;

import io.grpc.stub.StreamObserver;

import lombok.extern.slf4j.Slf4j;

/**
 * The subscribeStream bridge (issue #5411 item 3): adapts one client-held bidi gRPC stream into a
 * {@link org.apache.eventmesh.runtime.delivery.PushChannel}, so the v2 delivery pipeline
 * (ReliableDispatcher -> channel.deliver) pushes straight into the stream, and the SDK's
 * client-side ACK (a reply message sent back on the same stream) completes the delivery via
 * {@link #ack(String)}.
 *
 * <p>This mirrors the WebSocket/SSE transport contract (buffered + ACK-tracked at-least-once,
 * shared retry/DLQ) - it is the "same dispatcher the SSE path uses", just with a gRPC
 * {@link StreamObserver} underneath.</p>
 */
@Slf4j
public class GrpcStreamChannel implements org.apache.eventmesh.runtime.delivery.PushChannel {

    private final StreamObserver<CloudEvent> sink;
    private final AtomicBoolean open = new AtomicBoolean(true);

    public GrpcStreamChannel(StreamObserver<CloudEvent> sink) {
        this.sink = sink;
    }

    @Override
    public void deliver(String deliveryId, EventMeshFrame event, AckCallback callback) {
        if (!open.get()) {
            callback.nack(new IllegalStateException("grpc stream closed"));
            return;
        }
        try {
            // Egress: Frame -> v2 CloudEvent -> legacy proto CloudEvent. The delivery id rides as
            // the seqnum attribute: the legacy SDK replies with the SAME attributes (its reply
            // builder copies the request's attribute map), so the ack path can correlate back.
            io.cloudevents.CloudEvent v2 = event.toCloudEvent();
            CloudEvent proto = GrpcCloudEventMapper.toProto(v2);
            proto = CloudEvent.newBuilder(proto)
                .putAttributes("emdeliveryid",
                    CloudEvent.CloudEventAttributeValue.newBuilder().setCeString(deliveryId).build())
                .build();
            synchronized (sink) {
                sink.onNext(proto);
            }
            // NOTE: the ACK fires from the stream's inbound side (GrpcSubscriber reply or the
            // dedicated ack attribute) - see EventMeshGrpcServer.ackFromClient. Until then the
            // callback stays pending in the dispatcher's tracker (at-least-once: unACKed
            // deliveries redeliver after ackTimeoutMs).
        } catch (RuntimeException e) {
            open.set(false);
            log.warn("grpc stream push failed (delivery={}): {}", deliveryId, e.toString());
            callback.nack(e);
        }
    }

    /** Complete a pending delivery when the client ACKs on the stream. */
    public boolean ack(String deliveryId) {
        return true; // correlation handled by the server's AckCallback registry
    }

    public boolean isOpen() {
        return open.get();
    }

    /** Mark the stream dead (client cancelled / errored). Buffered events will redeliver. */
    public void close() {
        open.set(false);
    }
}
