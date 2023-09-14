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

package org.apache.eventmesh.connector.jdbc.source.dialect.mysql;


import org.apache.eventmesh.connector.jdbc.common.SourceInfo;

import lombok.Data;

@Data
public class MysqlSourceInfo implements SourceInfo {

    private String currentBinlogFileName;

    private long currentBinlogPosition = 0L;

    private int currentRowNumber = 0;

    public void setBinlogPosition(String binlogFileName, long beginProcessPosition) {
        if (binlogFileName != null) {
            this.currentBinlogFileName = binlogFileName;
        }
        assert beginProcessPosition >= 0;
        this.currentBinlogPosition = beginProcessPosition;
        this.currentRowNumber = 0;
    }
}
