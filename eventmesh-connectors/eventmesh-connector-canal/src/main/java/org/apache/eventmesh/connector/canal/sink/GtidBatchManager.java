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

package org.apache.eventmesh.connector.canal.sink;

import org.apache.eventmesh.connector.canal.CanalConnectRecord;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class GtidBatchManager {

    private static ConcurrentHashMap<String, GtidBatch> gtidBatchMap = new ConcurrentHashMap<>();

    public static void addBatch(String gtid, int batchIndex, int totalBatches, List<CanalConnectRecord> batchRecords) {
        gtidBatchMap.computeIfAbsent(gtid, k -> new GtidBatch(totalBatches)).addBatch(batchIndex, batchRecords);
    }

    public static GtidBatch getGtidBatch(String gtid) {
        return gtidBatchMap.get(gtid);
    }

    public static boolean isComplete(String gtid) {
        GtidBatch batch = gtidBatchMap.get(gtid);
        return batch != null && batch.isComplete();
    }

    public static void removeGtidBatch(String gtid) {
        gtidBatchMap.remove(gtid);
    }
}
