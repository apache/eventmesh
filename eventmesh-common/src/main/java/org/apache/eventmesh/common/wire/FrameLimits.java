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

/**
 * Wire-format bounds for {@link EventMeshFrame} (issue #5361, plan #5354 Phase 1).
 *
 * <p>The frame codec previously accepted any sizes the header advertised — a malformed or
 * hostile frame could declare {@code dataLen = 2^31-1} and the decoder would allocate that
 * much memory before failing on the truncated body (OOM instead of a clean reject). These
 * bounds are enforced at BOTH the encode boundary (a producer cannot build an oversized
 * frame) and the decode boundary (a consumer rejects oversized input without allocating).</p>
 *
 * <p>Values are intentionally generous for an internal wire format: a 16 MiB frame ceiling
 * covers streaming chunks and large CloudEvents payloads with headroom, while still
 * bounding a single frame's memory footprint. Attribute count/size bounds mirror typical
 * protocol header limits (HTTP/2: 100 headers; many brokers: 4 KiB per header value).</p>
 */
public final class FrameLimits {

    private FrameLimits() {
    }

    /** Maximum encoded frame size (header + attributes + data). */
    public static final int MAX_FRAME_BYTES = 16 * 1024 * 1024;

    /** Maximum payload (data) size in bytes. */
    public static final int MAX_DATA_BYTES = 8 * 1024 * 1024;

    /** Maximum number of attributes per frame. */
    public static final int MAX_ATTRIBUTES = 64;

    /** Maximum UTF-8 length of one attribute name, in bytes. */
    public static final int MAX_ATTR_NAME_BYTES = 256;

    /** Maximum UTF-8 length of one attribute value, in bytes. */
    public static final int MAX_ATTR_VALUE_BYTES = 4 * 1024;
}
