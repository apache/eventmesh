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

package org.apache.eventmesh.common.config.connector.rdb.canal;

import org.apache.eventmesh.common.config.connector.SourceConfig;
import org.apache.eventmesh.common.remote.job.SyncConsistency;
import org.apache.eventmesh.common.remote.job.SyncMode;
import org.apache.eventmesh.common.remote.offset.RecordPosition;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CanalSourceConfig extends SourceConfig {

    private String destination;

    private Long canalInstanceId;

    private String desc;

    private boolean ddlSync = true;

    private boolean filterTableError = false;

    private Long slaveId;

    private Short clientId;

    private Integer batchSize = 10000;

    private Long batchTimeout = -1L;

    private List<RecordPosition> recordPositions;

    // ================================= channel parameter
    // ================================

    private Boolean enableRemedy = false;                                             // 是否启用冲突补救算法

//    private RemedyAlgorithm remedyAlgorithm;                                          // 冲突补救算法

//    private Integer remedyDelayThresoldForMedia;                              // 针对回环补救，如果反查速度过快，容易查到旧版本的数据记录，导致中美不一致，所以设置一个阀值，低于这个阀值的延迟不进行反查

    private SyncMode syncMode;                                                 // 同步模式：字段/整条记录

    private SyncConsistency syncConsistency;                                          // 同步一致性要求

    // ================================= system parameter
    // ================================

    private String systemSchema;                                             // 默认为retl，不允许为空

    private String systemMarkTable;                                          // 双向同步标记表

    private String systemMarkTableColumn;                                    // 双向同步标记的列名

    private String systemMarkTableInfo;                                      // 双向同步标记的info信息，比如类似BI_SYNC

    private String systemBufferTable;                                        // otter同步buffer表

    private String systemDualTable;                                          // otter同步心跳表

    private SourceConnectorConfig sourceConnectorConfig;
}
