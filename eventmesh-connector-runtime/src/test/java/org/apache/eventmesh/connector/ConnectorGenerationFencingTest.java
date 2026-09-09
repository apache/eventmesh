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

package org.apache.eventmesh.connector;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Issue #5382 acceptance: the connector-assignment fencing decision — a stale worker's
 * delayed /control/start (generation lower than the running one) is rejected; equal is an
 * idempotent re-push; greater supersedes; stop clears the fence.
 */
class ConnectorGenerationFencingTest {

    @Test
    void staleGenerationStartIsRejected() {
        ConnectorManager manager = new ConnectorManager(null, null);
        manager.recordRunningGeneration("c1", 2L);
        assertFalse(manager.shouldAcceptStart("c1", 1L),
            "a delayed start from the superseded assignment must be rejected");
        assertTrue(manager.shouldAcceptStart("c1", 2L),
            "an equal generation is an idempotent re-push");
        assertTrue(manager.shouldAcceptStart("c1", 5L),
            "a newer generation supersedes the running one");
    }

    @Test
    void noRunningGenerationAcceptsAnything() {
        ConnectorManager manager = new ConnectorManager(null, null);
        assertTrue(manager.shouldAcceptStart("fresh", 0L),
            "a connector nobody runs accepts even a legacy gen-0 start");
    }

    @Test
    void stopClearsGenerationSoLegacyRestartWorks() {
        ConnectorManager manager = new ConnectorManager(null, null);
        manager.recordRunningGeneration("c1", 3L);
        manager.stopConnector("c1");
        assertTrue(manager.shouldAcceptStart("c1", 0L),
            "stop clears the fence; a fresh start re-baselines the generation");
    }
}
