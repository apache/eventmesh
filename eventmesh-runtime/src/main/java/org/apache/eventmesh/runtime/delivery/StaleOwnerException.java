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

package org.apache.eventmesh.runtime.delivery;

/**
 * A stale partition owner tried to produce a durable side effect after being fenced
 * (issue #5360, plan #5354 Phase 1). The ownership layer (Meta CAS + {@code FencingToken})
 * moved the partition to a new instance; the old owner must drop the partition instead of
 * writing offsets, ACKing the broker or routing to DLQ — otherwise the new owner's state
 * and the stale owner's writes interleave (split-brain duplicates).
 *
 * <p>Carried effect: the pull loop removes the partition from its owned set on the next
 * ownership refresh; the broker's POP invisibleTime redelivers in-flight messages to the
 * new owner, preserving at-least-once.</p>
 */
public final class StaleOwnerException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String topic;
    private final int partition;

    public StaleOwnerException(String topic, int partition) {
        super("stale owner fenced on " + topic + "#" + partition
            + " (a newer FencingToken holds the Meta assignment; dropping the partition)");
        this.topic = topic;
        this.partition = partition;
    }

    /** The topic of the fenced partition. */
    public String topic() {
        return topic;
    }

    /** The fenced partition index. */
    public int partition() {
        return partition;
    }
}
