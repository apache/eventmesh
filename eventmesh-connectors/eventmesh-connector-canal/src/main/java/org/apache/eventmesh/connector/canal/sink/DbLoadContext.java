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

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import lombok.Data;

@Data
public class DbLoadContext {

    private List<CanalConnectRecord> lastProcessedRecords;

    private List<CanalConnectRecord> prepareRecords;

    private List<CanalConnectRecord> processedRecords;

    private List<CanalConnectRecord> failedRecords;

    public DbLoadContext() {
        lastProcessedRecords = Collections.synchronizedList(new LinkedList<>());
        prepareRecords = Collections.synchronizedList(new LinkedList<>());
        processedRecords = Collections.synchronizedList(new LinkedList<>());
        failedRecords = Collections.synchronizedList(new LinkedList<>());
    }


}
