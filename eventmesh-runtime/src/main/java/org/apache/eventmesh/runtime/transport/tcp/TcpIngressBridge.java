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

import org.apache.eventmesh.runtime.ingress.UniIngressService;

import java.util.concurrent.CompletableFuture;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * Ingress side of the TCP compatibility bridge: turns legacy TCP client frames into calls on the
 * new {@link UniIngressService}, and resolves egress ACKs via {@link TcpAckRegistry}.
 *
 * <p>A TCP client speaking the old wire protocol therefore drives exactly the same core as an HTTP
 * CloudEvents client — publish goes through {@code ingress.publish}, LISTEN through
 * {@code ingress.subscribe}, and the client's RESPONSE frame ACKs an earlier push delivery. No TCP
 * session/group/rebalance code remains on the core path.</p>
 */
@Slf4j
public class TcpIngressBridge {

    private final UniIngressService ingress;
    private final TcpAckRegistry ackRegistry;
    private final TcpFrameDecoder decoder;

    public TcpIngressBridge(UniIngressService ingress, TcpAckRegistry ackRegistry, TcpFrameDecoder decoder) {
        this.ingress = ingress;
        this.ackRegistry = ackRegistry;
        this.decoder = decoder;
    }

    /**
     * Handle a raw frame from {@code clientId}. Returns a future that completes when the request is
     * processed (publish waits on storage; the others are synchronous).
     */
    public CompletableFuture<Void> onClientFrame(String clientId, byte[] frame) {
        TcpRequest req;
        try {
            req = decoder.decode(clientId, frame);
        } catch (RuntimeException e) {
            log.warn("tcp frame decode failed from {}", clientId, e);
            CompletableFuture<Void> f = new CompletableFuture<>();
            f.completeExceptionally(e);
            return f;
        }
        if (req == null) {
            return CompletableFuture.completedFuture(null);
        }
        switch (req.getKind()) {
            case PUBLISH:
                return ingress.publish(req.getTopic(), req.getEvent());
            case SUBSCRIBE:
                ingress.subscribe(req.getTopic(), req.getClientId(), req.getMode(), null);
                return CompletableFuture.completedFuture(null);
            case UNSUBSCRIBE:
                ingress.unsubscribe(req.getClientId());
                return CompletableFuture.completedFuture(null);
            case ACK:
                // Client ACKs an egress (push) delivery — resolve it through the registry.
                ackRegistry.onClientAck(req.getDeliveryId());
                return CompletableFuture.completedFuture(null);
            default:
                return CompletableFuture.completedFuture(null);
        }
    }
}
