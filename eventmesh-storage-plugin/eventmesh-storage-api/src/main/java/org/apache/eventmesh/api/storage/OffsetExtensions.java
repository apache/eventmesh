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

package org.apache.eventmesh.api.storage;

/**
 * CloudEvent extension attribute names for MQ physical offset propagation.
 *
 * <p>These extensions are written by storage plugins (Kafka / RocketMQ 4.x / 5.x)
 * during {@code poll()}, so every CloudEvent flowing through EventMesh carries
 * its origin MQ position. This eliminates EventMesh's self-generated logical
 * sequence number and aligns all four offset categories to the MQ's physical
 * offset:
 * <pre>
 *   write offset   (MQ physical offset at send time)
 *   pull offset   (MQ physical offset at poll time)
 *   push offset   (offset handed to ReliableDispatcher.deliver)
 *   ACK offset    (offset confirmed by client ACK)
 * </pre>
 *
 * <p>Extension names follow the CloudEvents spec: lower-case ASCII letters
 * and digits only (no hyphens).</p>
 */
public final class OffsetExtensions {

    /**
     * MQ physical offset (long). Written by storage plugins on poll.
     * Example: {@code event.getExtension(EM_MQ_OFFSET) → 123456L}
     */
    public static final String EM_MQ_OFFSET = "emmqoffset";

    /**
     * MQ partition / queue id (int). Written by storage plugins on poll.
     * Example: {@code event.getExtension(EM_MQ_PARTITION) → 3}
     */
    public static final String EM_MQ_PARTITION = "emmqpartition";

    private OffsetExtensions() {
        // utility class
    }

    /**
     * Read the MQ physical offset from a CloudEvent extension.
     *
     * @return the offset, or {@code -1L} if the extension is absent
     */
    public static long readMqOffset(io.cloudevents.CloudEvent event) {
        Object v = event.getExtension(EM_MQ_OFFSET);
        if (v == null) {
            return -1L;
        }
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    /**
     * Read the MQ partition / queue id from a CloudEvent extension.
     *
     * @return the partition, or {@code -1} if the extension is absent
     */
    public static int readMqPartition(io.cloudevents.CloudEvent event) {
        Object v = event.getExtension(EM_MQ_PARTITION);
        if (v == null) {
            return -1;
        }
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
