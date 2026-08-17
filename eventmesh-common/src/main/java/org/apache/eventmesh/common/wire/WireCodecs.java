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

package org.apache.eventmesh.common.wire;

import lombok.extern.slf4j.Slf4j;

/**
 * Entry point for the internal-wire codec. Returns the configured {@link WireCodec}: by default
 * {@link EventMeshFrameCodec}; override with {@code -Deventmesh.wire.codec=<fqcn>} (the class must
 * have a public no-arg constructor and implement {@link WireCodec}). Falls back to
 * {@link EventMeshFrameCodec} if the property is unset or the named class can't be loaded.
 *
 * <p>Callers use {@link #get()} instead of touching {@link EventMeshFrame} directly, so the wire
 * format stays pluggable from a single place.</p>
 */
@Slf4j
public final class WireCodecs {

    /** System property name overriding the wire codec implementation class. */
    public static final String CODEC_PROPERTY = "eventmesh.wire.codec";

    private static volatile WireCodec cached;

    private WireCodecs() {
    }

    /**
     * @return the configured {@link WireCodec} (default EventMeshFrameCodec, override via property).
     */
    public static WireCodec get() {
        WireCodec c = cached;
        if (c != null) {
            return c;
        }
        synchronized (WireCodecs.class) {
            if (cached == null) {
                cached = load();
            }
            return cached;
        }
    }

    private static WireCodec load() {
        String fqcn = System.getProperty(CODEC_PROPERTY, "").trim();
        if (!fqcn.isEmpty()) {
            try {
                Class<?> cls = Class.forName(fqcn, true, Thread.currentThread().getContextClassLoader());
                return (WireCodec) cls.getDeclaredConstructor().newInstance();
            } catch (Throwable t) {
                log.warn("failed to load WireCodec '{}' ({}); falling back to EventMeshFrameCodec", fqcn, t.toString());
            }
        }
        return new EventMeshFrameCodec();
    }
}
