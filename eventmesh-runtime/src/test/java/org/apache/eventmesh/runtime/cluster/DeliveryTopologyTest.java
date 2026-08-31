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

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DeliveryTopology#fromConfig} (issue #5309): null/blank defaults to
 * LOCAL_STICKY_PULL (backward compatible), exact enum names resolve, surrounding whitespace is
 * trimmed, and any other value fails fast (a typo must not silently degrade to single-instance
 * mode).
 */
class DeliveryTopologyTest {

    @Test
    void nullConfigDefaultsToLocalStickyPull() {
        assertEquals(DeliveryTopology.LOCAL_STICKY_PULL, DeliveryTopology.fromConfig(null));
    }

    @Test
    void emptyConfigDefaultsToLocalStickyPull() {
        assertEquals(DeliveryTopology.LOCAL_STICKY_PULL, DeliveryTopology.fromConfig(""));
    }

    @Test
    void blankConfigDefaultsToLocalStickyPull() {
        assertEquals(DeliveryTopology.LOCAL_STICKY_PULL, DeliveryTopology.fromConfig("   "));
    }

    @Test
    void exactNamesResolve() {
        assertEquals(DeliveryTopology.LOCAL_STICKY_PULL, DeliveryTopology.fromConfig("LOCAL_STICKY_PULL"));
        assertEquals(DeliveryTopology.PARTITION_OWNED_PULL, DeliveryTopology.fromConfig("PARTITION_OWNED_PULL"));
    }

    @Test
    void surroundingWhitespaceIsTrimmed() {
        assertEquals(DeliveryTopology.PARTITION_OWNED_PULL, DeliveryTopology.fromConfig("  PARTITION_OWNED_PULL  "));
    }

    @Test
    void unknownValueFailsFastWithValidNamesInMessage() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> DeliveryTopology.fromConfig("SHARDED_PULL"));
        assertTrue(e.getMessage().contains("SHARDED_PULL"));
        assertTrue(e.getMessage().contains("LOCAL_STICKY_PULL"));
        assertTrue(e.getMessage().contains("PARTITION_OWNED_PULL"));
    }

    @Test
    void lowercaseIsRejectedNotSilentlyDefaulted() {
        assertThrows(IllegalArgumentException.class,
            () -> DeliveryTopology.fromConfig("local_sticky_pull"));
    }
}
