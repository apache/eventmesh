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

import org.apache.eventmesh.common.config.connector.rdb.jdbc.JdbcSourceConfig;
import org.apache.eventmesh.common.config.connector.rdb.jdbc.SourceConnectorConfig;
import org.apache.eventmesh.connector.jdbc.PartitionOffSetContextPair;
import org.apache.eventmesh.connector.jdbc.UniversalJdbcContext;
import org.apache.eventmesh.connector.jdbc.context.mysql.MysqlOffsetContext;
import org.apache.eventmesh.connector.jdbc.context.mysql.MysqlPartition;
import org.apache.eventmesh.connector.jdbc.source.dialect.antlr4.mysql.MysqlAntlr4DdlParser;

import org.apache.commons.lang3.StringUtils;

import lombok.Getter;

public class MysqlJdbcContext extends UniversalJdbcContext<MysqlPartition, MysqlOffsetContext, MysqlAntlr4DdlParser> {

    @Getter
    private MysqlSourceInfo sourceInfo = new MysqlSourceInfo();

    private volatile long currentHandleEventSize = 0;

    private volatile boolean onTransaction = false;

    // need to load from store when start
    private String restartGtidSet;

    private String currentGtidSet;

    private String restartBinlogFilename;

    private long restartBinlogPosition;

    private String transactionId;

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
        this.onTransaction = true;
    }

    public void commitTransaction() {
        this.onTransaction = false;
        this.restartGtidSet = this.currentGtidSet;
        this.restartBinlogFilename = sourceInfo.getCurrentBinlogFileName();
        this.restartBinlogPosition = sourceInfo.getCurrentBinlogPosition() + this.currentHandleEventSize;
        resetTransactionId();
    }

    public void complete() {
        this.currentHandleEventSize = 0;
    }

    public void completedGtidSet(String gtidSet) {
        if (StringUtils.isNotBlank(gtidSet)) {
            String trimmedGtidSet = gtidSet.replace("\n", "").replace("\r", "");
            this.currentGtidSet = trimmedGtidSet;
            this.restartGtidSet = trimmedGtidSet;
        }
    }

    public String getGtidSet() {
        return this.currentGtidSet;
    }

    public void beginGtid(String gtid) {
        this.sourceInfo.beginGtid(gtid);
    }

    private void resetTransactionId() {
        transactionId = null;
    }

}
