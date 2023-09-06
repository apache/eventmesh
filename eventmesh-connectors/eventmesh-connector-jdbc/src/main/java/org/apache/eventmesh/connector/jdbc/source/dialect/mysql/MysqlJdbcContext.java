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

import org.apache.eventmesh.connector.jdbc.PartitionOffSetContextPair;
import org.apache.eventmesh.connector.jdbc.UniversalJdbcContext;
import org.apache.eventmesh.connector.jdbc.context.mysql.MysqlOffsetContext;
import org.apache.eventmesh.connector.jdbc.context.mysql.MysqlPartition;
import org.apache.eventmesh.connector.jdbc.source.config.JdbcSourceConfig;
import org.apache.eventmesh.connector.jdbc.source.config.SourceConnectorConfig;
import org.apache.eventmesh.connector.jdbc.source.dialect.antlr4.mysql.MysqlAntlr4DdlParser;

import org.apache.commons.lang3.StringUtils;

import lombok.Getter;

public class MysqlJdbcContext extends UniversalJdbcContext<MysqlPartition, MysqlOffsetContext, MysqlAntlr4DdlParser> {

    @Getter
    private MysqlSourceInfo sourceInfo = new MysqlSourceInfo();

    private volatile long currentHandleEventSize = 0;

    private volatile boolean isStartTransaction = false;

    private String restartGtidSet;

    private String currentGtidSet;

    private JdbcSourceConfig jdbcSourceConfig;

    public MysqlJdbcContext(PartitionOffSetContextPair<MysqlPartition, MysqlOffsetContext> poCtx, MysqlAntlr4DdlParser parser) {
        super(poCtx, parser);
    }

    public MysqlJdbcContext(MysqlPartition mysqlPartition, MysqlOffsetContext mysqlOffsetContext, MysqlAntlr4DdlParser parser) {
        super(mysqlPartition, mysqlOffsetContext, parser);
    }

    public MysqlJdbcContext(JdbcSourceConfig jdbcSourceConfig, MysqlAntlr4DdlParser parser) {
        this(new MysqlPartition(), new MysqlOffsetContext(), parser);
        this.jdbcSourceConfig = jdbcSourceConfig;
    }

    public void setBinlogStartPoint(String binlogFilename, long beginProcessPosition) {
        assert beginProcessPosition >= 0;
        if (binlogFilename != null) {
            sourceInfo.setBinlogPosition(binlogFilename, beginProcessPosition);

        } else {
            sourceInfo.setBinlogPosition(sourceInfo.getCurrentBinlogFileName(), beginProcessPosition);
        }
    }

    public void setEventPosition(long positionOfCurrentEvent, long eventSize) {
        this.sourceInfo.setCurrentBinlogPosition(positionOfCurrentEvent);
        this.currentHandleEventSize = eventSize;
    }

    public static MysqlJdbcContext initialize(JdbcSourceConfig jdbcSourceConfig) {
        SourceConnectorConfig sourceConnectorConfig = jdbcSourceConfig.getSourceConnectorConfig();
        MysqlAntlr4DdlParser mysqlAntlr4DdlParser = new MysqlAntlr4DdlParser(sourceConnectorConfig.isSkipViews(),
            sourceConnectorConfig.isSkipComments(), jdbcSourceConfig);
        return new MysqlJdbcContext(new MysqlPartition(), new MysqlOffsetContext(), mysqlAntlr4DdlParser);
    }

    public void startTransaction() {
        this.isStartTransaction = true;
    }

    public void commitTransaction() {
        this.isStartTransaction = false;
    }

    public void complete() {
        //TODO: What to do?
        this.currentHandleEventSize = 0;
    }

    public void setCompletedGtidSet(String gtidSet) {
        if (StringUtils.isNotBlank(gtidSet)) {
            String trimmedGtidSet = gtidSet.replace("\n", "").replace("\r", "");
            this.currentGtidSet = trimmedGtidSet;
            this.restartGtidSet = trimmedGtidSet;
        }
    }

    public String getGtidSet() {
        return this.currentGtidSet != null ? this.currentGtidSet : null;
    }


}
