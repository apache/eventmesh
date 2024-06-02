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

import org.apache.eventmesh.common.config.connector.SinkConfig;
import org.apache.eventmesh.common.remote.job.SyncMode;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CanalSinkConfig extends SinkConfig {

    private Integer batchSize = 50;                          // batchSize

    private Boolean useBatch = true;                        // enable batch

    private Integer poolSize = 5;                           // sink thread size for single channel

    private SyncMode syncMode;                              // sync mode: field/row

    private Boolean skipException = false;                  // skip sink process exception

    public SinkConnectorConfig sinkConnectorConfig;

}
