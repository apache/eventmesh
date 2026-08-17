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

package org.apache.eventmesh.runtime.ingress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class LoadMeterTest {

    @Test
    void snapshotReflectsActiveSessionsAndRates() throws Exception {
        AtomicInteger sessions = new AtomicInteger(3);
        LoadMeter meter = new LoadMeter(sessions::get);
        meter.recordInflow(1000);
        meter.recordOutflow(500);
        Thread.sleep(5); // let some time elapse so rates are computed
        meter.sample();
        LoadMeter.Snapshot snap = meter.snapshot();
        assertEquals(3, snap.activeSessions);
        assertTrue(snap.inflowBytesPerSec > 0, "inflow rate should be positive after recording");
        assertTrue(snap.outflowBytesPerSec > 0, "outflow rate should be positive after recording");
    }

    @Test
    void perClientInflowBucketsAccumulate() {
        LoadMeter meter = new LoadMeter(() -> 0);
        meter.recordInflow("c1", 100);
        meter.recordInflow("c1", 50);
        meter.recordInflow("c2", 200);
        assertEquals(150L, meter.clientInflowSnapshot().get("c1"));
        assertEquals(200L, meter.clientInflowSnapshot().get("c2"));
    }

    @Test
    void snapshotLoadParsesFourFields() {
        LoadMeter.Snapshot s = LoadMeter.Snapshot.parseLoad(new String[] {"5", "1200", "800", "0.25"});
        assertEquals(5, s.activeSessions);
        assertEquals(1200L, s.inflowBytesPerSec);
        assertEquals(0.25, s.cpuLoad, 1e-9);
    }

    @Test
    void snapshotLoadReturnsNullForMissingFields() {
        assertEquals(null, LoadMeter.Snapshot.parseLoad(null));
        assertEquals(null, LoadMeter.Snapshot.parseLoad(new String[] {"5"}));
    }

    @Test
    void sampleTwiceResetsWindow() throws Exception {
        LoadMeter meter = new LoadMeter(() -> 0);
        meter.recordInflow(2000);
        Thread.sleep(5);
        meter.sample();
        long firstRate = meter.snapshot().inflowBytesPerSec;
        // No new inflow → second sample should drop toward zero.
        Thread.sleep(5);
        meter.sample();
        assertTrue(meter.snapshot().inflowBytesPerSec < firstRate,
            "rate should drop without new inflow");
    }
}
