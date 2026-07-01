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

package org.apache.eventmesh.connector.jdbc;

public final class PartitionOffSetContextPair<Part extends Partition, Offset extends OffsetContext> {

    private Part partition;

    private Offset offsetContext;

    private PartitionOffSetContextPair(Part partition, Offset offsetContext) {
        this.partition = partition;
        this.offsetContext = offsetContext;
    }

    public static <Part extends Partition, Offset extends OffsetContext> PartitionOffSetContextPair<Part, Offset> of(Part partition, Offset offset) {
        return new PartitionOffSetContextPair<>(partition, offset);
    }

    public Part getPartition() {
        return partition;
    }

    public Offset getOffsetContext() {
        return offsetContext;
    }
}
