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

package org.apache.eventmesh.protocol.api;

import org.apache.eventmesh.common.protocol.ByteTransport;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

/**
 * Entry point for protocol-adaptor SPI loading. Caches {@link FrameAdaptor} instances by protocol
 * name ({@code "cloudevents"}, {@code "meshmessage"}, …); falls back to the CloudEvents adaptor
 * when a named adaptor can't be loaded. Callers use {@link #get(String)} instead of touching
 * {@code EventMeshFrame.fromCloudEvent} / {@code MeshMessageFrameCodec} directly, so all protocol
 * conversion logic lives in the adaptors.
 */
@Slf4j
public final class FrameAdaptors {

    /** Default protocol name when the caller doesn't specify (HTTP CloudEvents path). */
    public static final String DEFAULT = "cloudevents";

    private static final ConcurrentHashMap<String, FrameAdaptor> CACHE = new ConcurrentHashMap<>();

    private FrameAdaptors() {
    }

    /**
     * @return the {@link FrameAdaptor} for {@code protocolName} (cached; CloudEvents fallback).
     */
    public static FrameAdaptor get(String protocolName) {
        String name = (protocolName == null || protocolName.isEmpty()) ? DEFAULT : protocolName;
        return CACHE.computeIfAbsent(name, FrameAdaptors::load);
    }

    /** Convenience: the default (CloudEvents) adaptor. */
    public static FrameAdaptor cloudevents() {
        return get(DEFAULT);
    }

    private static FrameAdaptor load(String name) {
        try {
            FrameAdaptor adaptor = EventMeshExtensionFactory.getExtension(FrameAdaptor.class, name);
            if (adaptor != null) {
                return adaptor;
            }
        } catch (Throwable t) {
            log.warn("FrameAdaptor '{}' load failed ({}); trying default", name, t.toString());
        }
        // If the named adaptor isn't found, try the default (cloudevents) — it's registered in the
        // separate eventmesh-protocol-cloudevents plugin on the classpath.
        if (!DEFAULT.equals(name)) {
            try {
                FrameAdaptor fallback = EventMeshExtensionFactory.getExtension(FrameAdaptor.class, DEFAULT);
                if (fallback != null) {
                    log.warn("FrameAdaptor '{}' not found; using default ({})", name, DEFAULT);
                    return fallback;
                }
            } catch (Throwable ignored) {
                // best-effort
            }
        }
        throw new IllegalStateException("FrameAdaptor '" + name + "' not found (is the protocol plugin on the classpath?)");
    }

    // ---- convenience wrappers for the common CloudEvents path ----

    /** Ingress: CloudEvents-JSON bytes → EventMeshFrame (via the default CloudEvents adaptor). */
    public static EventMeshFrame toFrame(byte[] cloudEventsJson) {
        return cloudevents().toFrameSilent(new ByteTransport(cloudEventsJson));
    }

    /** Egress: EventMeshFrame → CloudEvents-JSON bytes (via the default CloudEvents adaptor). */
    public static byte[] toCloudEventsJson(EventMeshFrame frame) {
        ProtocolTransportObject proto = cloudevents().fromFrameSilent(frame);
        if (proto instanceof ByteTransport) {
            return ((ByteTransport) proto).getBytes();
        }
        return new byte[0];
    }
}
