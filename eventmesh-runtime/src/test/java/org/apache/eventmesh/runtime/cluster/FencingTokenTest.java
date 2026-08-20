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

package org.apache.eventmesh.runtime.cluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FencingToken} (§13.2.8④).
 */
class FencingTokenTest {

    @Test
    void nextIsStrictlyGreater() {
        FencingToken t = new FencingToken(1000L, new AtomicLong(0));
        FencingToken t1 = t.next();
        FencingToken t2 = t.next();
        assertTrue(t1.compareTo(t) > 0, "next() > original");
        assertTrue(t2.compareTo(t1) > 0, "second next() > first next()");
    }

    @Test
    void differentBootEpochOrdersByEpochFirst() {
        FencingToken old = new FencingToken(1000L, new AtomicLong(5));
        FencingToken newer = new FencingToken(2000L, new AtomicLong(0));
        assertTrue(newer.compareTo(old) > 0, "higher bootEpoch wins regardless of counter");
        assertTrue(old.compareTo(newer) < 0);
    }

    @Test
    void sameBootEpochOrdersByCounter() {
        FencingToken a = new FencingToken(1000L, new AtomicLong(3));
        FencingToken b = new FencingToken(1000L, new AtomicLong(7));
        assertTrue(b.compareTo(a) > 0);
        assertTrue(a.compareTo(b) < 0);
    }

    @Test
    void equalTokensCompareAsZero() {
        FencingToken a = new FencingToken(1000L, new AtomicLong(5));
        FencingToken b = new FencingToken(1000L, new AtomicLong(5));
        assertEquals(0, a.compareTo(b));
    }

    @Test
    void toStringRoundTrip() {
        FencingToken t = new FencingToken(1234567890L, new AtomicLong(42));
        String s = t.toString();
        assertEquals("1234567890:42", s);
        FencingToken parsed = FencingToken.parse(s);
        assertEquals(0, t.compareTo(parsed), "parse(toString()) should be equal");
    }

    @Test
    void parseRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> FencingToken.parse(null));
    }

    @Test
    void parseRejectsMissingColon() {
        assertThrows(IllegalArgumentException.class, () -> FencingToken.parse("noColon"));
    }

    @Test
    void parseRejectsNonNumeric() {
        assertThrows(IllegalArgumentException.class, () -> FencingToken.parse("abc:def"));
    }

    @Test
    void defaultConstructorUsesCurrentTime() {
        long before = System.currentTimeMillis();
        FencingToken t = new FencingToken();
        long after = System.currentTimeMillis();
        assertTrue(t.bootEpoch() >= before, "bootEpoch >= before");
        assertTrue(t.bootEpoch() <= after, "bootEpoch <= after");
    }

    @Test
    void nextPreservesBootEpoch() {
        FencingToken t = new FencingToken(5000L, new AtomicLong(0));
        FencingToken t1 = t.next();
        FencingToken t2 = t.next();
        assertEquals(5000L, t1.bootEpoch());
        assertEquals(5000L, t2.bootEpoch());
    }
}
