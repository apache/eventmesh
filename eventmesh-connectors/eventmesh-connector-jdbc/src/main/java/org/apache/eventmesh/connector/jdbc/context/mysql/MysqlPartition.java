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

package org.apache.eventmesh.connector.jdbc.context.mysql;

import org.apache.eventmesh.connector.jdbc.AbstractPartition;
import org.apache.eventmesh.connector.jdbc.Partition;
import org.apache.eventmesh.connector.jdbc.table.catalog.TableId;

import java.util.HashMap;
import java.util.Map;

public class MysqlPartition extends AbstractPartition implements Partition {

    private static final String TABLE_ID = "tableId";

    private Map<String, String> partitions = new HashMap<>();

    public MysqlPartition(TableId tableId) {
        partitions.put(TABLE_ID, tableId.toString());
    }

    public MysqlPartition() {

    }

    @Override
    public Map<String, String> getPartition() {
        return partitions;
    }
}
