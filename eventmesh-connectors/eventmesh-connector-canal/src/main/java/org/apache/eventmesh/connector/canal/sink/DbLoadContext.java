/*
 * Copyright (C) 2010-2101 Alibaba Group Holding Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

/**
 * 数据库处理上下文
 */
@Data
public class DbLoadContext {

    private List<CanalConnectRecord> lastProcessedRecords;                      // 上一轮的已录入的记录，可能会有多次失败需要合并多次已录入的数据

    private List<CanalConnectRecord> prepareRecords;                            // 准备处理的数据

    private List<CanalConnectRecord> processedRecords;                          // 已处理完成的数据

    private List<CanalConnectRecord> failedRecords;

    public DbLoadContext() {
        lastProcessedRecords = Collections.synchronizedList(new LinkedList<>());
        prepareRecords = Collections.synchronizedList(new LinkedList<>());
        processedRecords = Collections.synchronizedList(new LinkedList<>());
        failedRecords = Collections.synchronizedList(new LinkedList<>());
    }


}
