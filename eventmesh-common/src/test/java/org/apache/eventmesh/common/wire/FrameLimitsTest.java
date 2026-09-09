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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * Issue #5361 acceptance (plan #5354 Phase 1): the frame codec enforces {@link FrameLimits} at
 * BOTH boundaries — encode fails fast on oversized input, decode rejects malformed/hostile
 * headers <em>without allocating</em> — and a randomized fuzz pass never throws anything but
 * {@link IllegalArgumentException} (no OOM, no ArrayIndexOutOfBounds escape, no infinite loop).
 */
class FrameLimitsTest {

    private static EventMeshFrame frame(Map<String, String> attrs, byte[] data) {
        return EventMeshFrame.event(attrs, data);
    }

    // -------------------- encode bounds --------------------

    @Test
    void encodeRejectsTooManyAttributes() {
        Map<String, String> attrs = new LinkedHashMap<>();
        for (int i = 0; i <= FrameLimits.MAX_ATTRIBUTES; i++) {
            attrs.put("k" + i, "v");
        }
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> frame(attrs, new byte[0]).encode());
        assertTrue(ex.getMessage().contains("attribute count"), ex.getMessage());
    }

    @Test
    void encodeRejectsOversizedData() {
        // MAX_DATA_BYTES + 1 (safe: 8 MiB + 1 allocation only)
        byte[] data = new byte[FrameLimits.MAX_DATA_BYTES + 1];
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> frame(new LinkedHashMap<>(), data).encode());
        assertTrue(ex.getMessage().contains("data length"), ex.getMessage());
    }

    @Test
    void encodeRejectsOversizedAttributeName() {
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("k".repeat(FrameLimits.MAX_ATTR_NAME_BYTES + 1), "v");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> frame(attrs, new byte[0]).encode());
        assertTrue(ex.getMessage().contains("attribute name length"), ex.getMessage());
    }

    @Test
    void encodeRejectsOversizedAttributeValue() {
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("k", "v".repeat(FrameLimits.MAX_ATTR_VALUE_BYTES + 1));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> frame(attrs, new byte[0]).encode());
        assertTrue(ex.getMessage().contains("attribute value length"), ex.getMessage());
    }

    @Test
    void encodeAcceptsAtTheLimits() {
        // Exactly at every limit must pass (boundary is > not >=).
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("k".repeat(FrameLimits.MAX_ATTR_NAME_BYTES), "v".repeat(FrameLimits.MAX_ATTR_VALUE_BYTES));
        byte[] data = new byte[FrameLimits.MAX_DATA_BYTES];
        EventMeshFrame f = frame(attrs, data);
        byte[] encoded = f.encode();
        assertEquals(FrameLimits.MAX_DATA_BYTES, EventMeshFrame.decode(encoded).data().length);
    }

    // -------------------- decode bounds (no-allocation rejects) --------------------

    @Test
    void decodeRejectsHostileDataLenWithoutAllocating() {
        // Hand-craft a header that advertises dataLen = Integer.MAX_VALUE on a tiny buffer.
        ByteBuffer hostile = ByteBuffer.allocate(FrameLimits.MAX_FRAME_BYTES > 64 ? 64 : FrameLimits.MAX_FRAME_BYTES);
        hostile.put((byte) EventMeshFrame.MAGIC);
        hostile.put((byte) EventMeshFrame.VERSION);
        hostile.put((byte) 3); // TYPE_EVENT
        hostile.put((byte) 0); // flags
        hostile.putInt(1);     // seq
        hostile.putShort((short) 0); // keyCount
        hostile.putInt(Integer.MAX_VALUE); // dataLen — hostile
        byte[] bytes = hostile.array();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> EventMeshFrame.decode(bytes));
        assertTrue(ex.getMessage().contains("data length") || ex.getMessage().contains("buffer"),
            ex.getMessage());
    }

    @Test
    void decodeRejectsHostileKeyCount() {
        ByteBuffer hostile = ByteBuffer.allocate(64);
        hostile.put((byte) EventMeshFrame.MAGIC);
        hostile.put((byte) EventMeshFrame.VERSION);
        hostile.put((byte) 3);
        hostile.put((byte) 0);
        hostile.putInt(1);
        hostile.putShort((short) (FrameLimits.MAX_ATTRIBUTES + 1)); // keyCount — hostile
        hostile.putInt(0);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> EventMeshFrame.decode(hostile.array()));
        assertTrue(ex.getMessage().contains("attribute count"), ex.getMessage());
    }

    @Test
    void decodeRejectsDataLenBeyondProvidedBuffer() {
        // Well-formed frame with 10 data bytes, but the buffer is truncated to the header only.
        EventMeshFrame f = frame(mapOf("k", "v"), new byte[10]);
        byte[] full = f.encode();
        byte[] truncated = new byte[14]; // HEADER_LEN only
        System.arraycopy(full, 0, truncated, 0, truncated.length);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> EventMeshFrame.decode(truncated));
        assertTrue(ex.getMessage().contains("buffer"), ex.getMessage());
    }

    // -------------------- fuzz --------------------

    @Test
    void fuzzedHeadersNeverEscapeIllegalArgumentException() {
        // Deterministic fuzz: mutate a well-formed frame's bytes; every decode outcome is
        // either a valid frame or an IllegalArgumentException — never OOM/AIOOBE/infinite loop.
        Random rnd = new Random(20260909L);
        EventMeshFrame wellFormed = frame(mapOf("k1", "v1", "k2", "v2"), new byte[32]);
        byte[] encoded = wellFormed.encode();
        int illegal = 0;
        int accepted = 0;
        for (int iter = 0; iter < 20_000; iter++) {
            byte[] mutant = encoded.clone();
            int mutations = 1 + rnd.nextInt(4);
            for (int m = 0; m < mutations; m++) {
                mutant[rnd.nextInt(mutant.length)] = (byte) rnd.nextInt(256);
            }
            try {
                EventMeshFrame decoded = EventMeshFrame.decode(mutant);
                assertNotNull(decoded);
                accepted++;
            } catch (IllegalArgumentException expected) {
                illegal++;
            }
        }
        assertTrue(accepted > 0, "some mutants should still decode");
        assertTrue(illegal > 0, "some mutants should be rejected");
    }

    private static Map<String, String> mapOf(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }
}
