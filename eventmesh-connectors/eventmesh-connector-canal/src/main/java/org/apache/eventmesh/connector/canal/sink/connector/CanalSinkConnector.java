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

package org.apache.eventmesh.connector.canal.sink.connector;

import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.config.connector.rdb.canal.CanalSinkConfig;
import org.apache.eventmesh.connector.canal.CanalConnectRecord;
import org.apache.eventmesh.connector.canal.DatabaseConnection;
import org.apache.eventmesh.connector.canal.SqlUtils;
import org.apache.eventmesh.connector.canal.dialect.DbDialect;
import org.apache.eventmesh.connector.canal.dialect.MysqlDialect;
import org.apache.eventmesh.connector.canal.interceptor.SqlBuilderLoadInterceptor;
import org.apache.eventmesh.connector.canal.model.EventColumn;
import org.apache.eventmesh.connector.canal.model.EventType;
import org.apache.eventmesh.connector.canal.sink.DbLoadContext;
import org.apache.eventmesh.connector.canal.sink.DbLoadData;
import org.apache.eventmesh.connector.canal.sink.DbLoadData.TableLoadData;
import org.apache.eventmesh.connector.canal.sink.DbLoadMerger;
import org.apache.eventmesh.connector.canal.source.table.RdbTableMgr;
import org.apache.eventmesh.openconnect.api.ConnectorCreateService;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SinkConnectorContext;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;


import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.StatementCallback;
import org.springframework.jdbc.core.StatementCreatorUtils;
import org.springframework.jdbc.support.lob.DefaultLobHandler;
import org.springframework.jdbc.support.lob.LobCreator;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.util.CollectionUtils;

import com.alibaba.otter.canal.common.utils.NamedThreadFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CanalSinkConnector implements Sink, ConnectorCreateService<Sink> {

    private CanalSinkConfig sinkConfig;

    private JdbcTemplate jdbcTemplate;

    private SqlBuilderLoadInterceptor interceptor;

    private DbDialect dbDialect;

    private ExecutorService executor;

    private int batchSize = 50;

    private boolean useBatch = true;
    private RdbTableMgr tableMgr;

    @Override
    public Class<? extends Config> configClass() {
        return CanalSinkConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
        // init config for canal source connector
        this.sinkConfig = (CanalSinkConfig) config;
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        // init config for canal source connector
        SinkConnectorContext sinkConnectorContext = (SinkConnectorContext) connectorContext;
        this.sinkConfig = (CanalSinkConfig) sinkConnectorContext.getSinkConfig();
        this.batchSize = sinkConfig.getBatchSize();
        this.useBatch = sinkConfig.getUseBatch();
        DatabaseConnection.sinkConfig = this.sinkConfig.getSinkConnectorConfig();
        DatabaseConnection.initSinkConnection();
        jdbcTemplate = new JdbcTemplate(DatabaseConnection.sinkDataSource);
        dbDialect = new MysqlDialect(jdbcTemplate, new DefaultLobHandler());
        interceptor = new SqlBuilderLoadInterceptor();
        interceptor.setDbDialect(dbDialect);
        tableMgr = new RdbTableMgr(sinkConfig.getSinkConnectorConfig(), DatabaseConnection.sinkDataSource);
        executor = new ThreadPoolExecutor(sinkConfig.getPoolSize(),
            sinkConfig.getPoolSize(),
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(sinkConfig.getPoolSize() * 4),
            new NamedThreadFactory("canalSink"),
            new ThreadPoolExecutor.CallerRunsPolicy());
    }

    @Override
    public void start() throws Exception {
        tableMgr.start();
    }

    @Override
    public void commit(ConnectRecord record) {

    }

    @Override
    public String name() {
        return this.sinkConfig.getSinkConnectorConfig().getConnectorName();
    }

    @Override
    public void stop() {
        executor.shutdown();
    }

    @Override
    public void put(List<ConnectRecord> sinkRecords) {
        DbLoadContext context = new DbLoadContext();
        for (ConnectRecord connectRecord : sinkRecords) {
            List<CanalConnectRecord> canalConnectRecordList = (List<CanalConnectRecord>) connectRecord.getData();
            canalConnectRecordList = filterRecord(canalConnectRecordList);
            if (isDdlDatas(canalConnectRecordList)) {
                doDdl(context, canalConnectRecordList);
            } else {
                canalConnectRecordList = DbLoadMerger.merge(canalConnectRecordList);

                DbLoadData loadData = new DbLoadData();
                doBefore(canalConnectRecordList, loadData);

                doLoad(context, sinkConfig, loadData);

            }

        }
    }

    @Override
    public Sink create() {
        return new CanalSinkConnector();
    }

    private boolean isDdlDatas(List<CanalConnectRecord> canalConnectRecordList) {
        boolean result = false;
        for (CanalConnectRecord canalConnectRecord : canalConnectRecordList) {
            result |= canalConnectRecord.getEventType().isDdl();
            if (result && !canalConnectRecord.getEventType().isDdl()) {
                throw new RuntimeException("ddl/dml can't be in one batch, it's may be a bug , pls submit issues.");
            }
        }
        return result;
    }

    private List<CanalConnectRecord> filterRecord(List<CanalConnectRecord> canalConnectRecordList) {
        return canalConnectRecordList.stream()
            .filter(record -> tableMgr.getTable(record.getSchemaName(), record.getTableName()) != null)
            .collect(Collectors.toList());
    }

    private void doDdl(DbLoadContext context, List<CanalConnectRecord> canalConnectRecordList) {
        for (final CanalConnectRecord record : canalConnectRecordList) {
            try {
                Boolean result = jdbcTemplate.execute(new StatementCallback<Boolean>() {

                    public Boolean doInStatement(Statement stmt) throws SQLException, DataAccessException {
                        boolean result = true;
                        if (StringUtils.isNotEmpty(record.getDdlSchemaName())) {
                            result &= stmt.execute("use `" + record.getDdlSchemaName() + "`");
                        }
                        result &= stmt.execute(record.getSql());
                        return result;
                    }
                });
                if (Boolean.TRUE.equals(result)) {
                    context.getProcessedRecords().add(record);
                } else {
                    context.getFailedRecords().add(record);
                }
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void doBefore(List<CanalConnectRecord> canalConnectRecordList, final DbLoadData loadData) {
        for (final CanalConnectRecord record : canalConnectRecordList) {
            boolean filter = interceptor.before(sinkConfig, record);
            if (!filter) {
                loadData.merge(record);
            }
        }
    }

    private void doLoad(DbLoadContext context, CanalSinkConfig sinkConfig, DbLoadData loadData) {
        List<List<CanalConnectRecord>> batchDatas = new ArrayList<>();
        for (TableLoadData tableData : loadData.getTables()) {
            if (useBatch) {
                batchDatas.addAll(split(tableData.getDeleteDatas()));
            } else {
                for (CanalConnectRecord data : tableData.getDeleteDatas()) {
                    batchDatas.add(Arrays.asList(data));
                }
            }
        }

        doTwoPhase(context, sinkConfig, batchDatas, true);

        batchDatas.clear();

        for (TableLoadData tableData : loadData.getTables()) {
            if (useBatch) {
                batchDatas.addAll(split(tableData.getInsertDatas()));
                batchDatas.addAll(split(tableData.getUpdateDatas()));
            } else {
                for (CanalConnectRecord data : tableData.getInsertDatas()) {
                    batchDatas.add(Arrays.asList(data));
                }
                for (CanalConnectRecord data : tableData.getUpdateDatas()) {
                    batchDatas.add(Arrays.asList(data));
                }
            }
        }

        doTwoPhase(context, sinkConfig, batchDatas, true);

        batchDatas.clear();
    }

    private List<List<CanalConnectRecord>> split(List<CanalConnectRecord> records) {
        List<List<CanalConnectRecord>> result = new ArrayList<>();
        if (records == null || records.isEmpty()) {
            return result;
        } else {
            int[] bits = new int[records.size()];
            for (int i = 0; i < bits.length; i++) {
                while (i < bits.length && bits[i] == 1) {
                    i++;
                }

                if (i >= bits.length) {
                    break;
                }

                List<CanalConnectRecord> batch = new ArrayList<>();
                bits[i] = 1;
                batch.add(records.get(i));
                for (int j = i + 1; j < bits.length && batch.size() < batchSize; j++) {
                    if (bits[j] == 0 && canBatch(records.get(i), records.get(j))) {
                        batch.add(records.get(j));
                        bits[j] = 1;
                    }
                }
                result.add(batch);
            }

            return result;
        }
    }

    private boolean canBatch(CanalConnectRecord source, CanalConnectRecord target) {
        return StringUtils.equals(source.getSchemaName(),
            target.getSchemaName())
            && StringUtils.equals(source.getTableName(), target.getTableName())
            && StringUtils.equals(source.getSql(), target.getSql());
    }

    private void doTwoPhase(DbLoadContext context, CanalSinkConfig sinkConfig, List<List<CanalConnectRecord>> totalRows, boolean canBatch) {
        List<Future<Exception>> results = new ArrayList<Future<Exception>>();
        for (List<CanalConnectRecord> rows : totalRows) {
            if (CollectionUtils.isEmpty(rows)) {
                continue;
            }
            results.add(executor.submit(new DbLoadWorker(context, rows, dbDialect, canBatch)));
        }

        boolean partFailed = false;
        for (Future<Exception> result : results) {
            Exception ex = null;
            try {
                ex = result.get();
            } catch (Exception e) {
                ex = e;
            }

            if (ex != null) {
                log.warn("##load phase one failed!", ex);
                partFailed = true;
            }
        }

        if (partFailed) {
            List<CanalConnectRecord> retryRecords = new ArrayList<>();
            for (List<CanalConnectRecord> rows : totalRows) {
                retryRecords.addAll(rows);
            }

            context.getFailedRecords().clear();

            Boolean skipException = sinkConfig.getSkipException();
            if (skipException != null && skipException) {
                for (CanalConnectRecord retryRecord : retryRecords) {
                    DbLoadWorker worker = new DbLoadWorker(context, Arrays.asList(retryRecord), dbDialect, false);
                    try {
                        Exception ex = worker.call();
                        if (ex != null) {
                            // do skip
                            log.warn("skip exception for data : {} , caused by {}",
                                retryRecord,
                                ExceptionUtils.getFullStackTrace(ex));
                        }
                    } catch (Exception ex) {
                        // do skip
                        log.warn("skip exception for data : {} , caused by {}",
                            retryRecord,
                            ExceptionUtils.getFullStackTrace(ex));
                    }
                }
            } else {
                DbLoadWorker worker = new DbLoadWorker(context, retryRecords, dbDialect, false);
                try {
                    Exception ex = worker.call();
                    if (ex != null) {
                        throw ex;
                    }
                } catch (Exception ex) {
                    log.error("##load phase two failed!", ex);
                    throw new RuntimeException(ex);
                }
            }
        }
    }

    enum ExecuteResult {
        SUCCESS, ERROR, RETRY
    }

    class DbLoadWorker implements Callable<Exception> {

        private final DbLoadContext context;
        private final DbDialect dbDialect;
        private final List<CanalConnectRecord> records;
        private final boolean canBatch;
        private final List<CanalConnectRecord> allFailedRecords = new ArrayList<>();
        private final List<CanalConnectRecord> allProcessedRecords = new ArrayList<>();
        private final List<CanalConnectRecord> processedRecords = new ArrayList<>();
        private final List<CanalConnectRecord> failedRecords = new ArrayList<>();

        public DbLoadWorker(DbLoadContext context, List<CanalConnectRecord> records, DbDialect dbDialect, boolean canBatch) {
            this.context = context;
            this.records = records;
            this.canBatch = canBatch;
            this.dbDialect = dbDialect;
        }

        public Exception call() throws Exception {
            try {
                return doCall();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private Exception doCall() {
            RuntimeException error = null;
            ExecuteResult exeResult = null;
            int index = 0;
            while (index < records.size()) {
                final List<CanalConnectRecord> splitDatas = new ArrayList<>();
                if (useBatch && canBatch) {
                    int end = Math.min(index + batchSize, records.size());
                    splitDatas.addAll(records.subList(index, end));
                    index = end;
                } else {
                    splitDatas.add(records.get(index));
                    index = index + 1;
                }

                int retryCount = 0;
                while (true) {
                    try {
                        if (!CollectionUtils.isEmpty(failedRecords)) {
                            splitDatas.clear();
                            splitDatas.addAll(failedRecords);
                        } else {
                            failedRecords.addAll(splitDatas);
                        }

                        final LobCreator lobCreator = dbDialect.getLobHandler().getLobCreator();
                        if (useBatch && canBatch) {
                            final String sql = splitDatas.get(0).getSql();
                            int[] affects = new int[splitDatas.size()];
                            affects = (int[]) dbDialect.getTransactionTemplate().execute((TransactionCallback) status -> {
                                try {
                                    failedRecords.clear();
                                    processedRecords.clear();
                                    JdbcTemplate template = dbDialect.getJdbcTemplate();
                                    int[] affects1 = template.batchUpdate(sql, new BatchPreparedStatementSetter() {

                                        public void setValues(PreparedStatement ps, int idx) throws SQLException {
                                            doPreparedStatement(ps, dbDialect, lobCreator, splitDatas.get(idx));
                                        }

                                        public int getBatchSize() {
                                            return splitDatas.size();
                                        }
                                    });
                                    return affects1;
                                } finally {
                                    lobCreator.close();
                                }
                            });

                            for (int i = 0; i < splitDatas.size(); i++) {
                                assert affects != null;
                                processStat(splitDatas.get(i), affects[i], true);
                            }
                        } else {
                            final CanalConnectRecord record = splitDatas.get(0);
                            int affect = 0;
                            affect = (Integer) dbDialect.getTransactionTemplate().execute((TransactionCallback) status -> {
                                try {
                                    failedRecords.clear();
                                    processedRecords.clear();
                                    JdbcTemplate template = dbDialect.getJdbcTemplate();
                                    int affect1 = template.update(record.getSql(), new PreparedStatementSetter() {

                                        public void setValues(PreparedStatement ps) throws SQLException {
                                            doPreparedStatement(ps, dbDialect, lobCreator, record);
                                        }
                                    });
                                    return affect1;
                                } finally {
                                    lobCreator.close();
                                }
                            });
                            processStat(record, affect, false);
                        }

                        error = null;
                        exeResult = ExecuteResult.SUCCESS;
                    } catch (DeadlockLoserDataAccessException ex) {
                        error = new RuntimeException(ExceptionUtils.getFullStackTrace(ex));
                        exeResult = ExecuteResult.RETRY;
                    } catch (Throwable ex) {
                        error = new RuntimeException(ExceptionUtils.getFullStackTrace(ex));
                        exeResult = ExecuteResult.ERROR;
                    }

                    if (ExecuteResult.SUCCESS == exeResult) {
                        allFailedRecords.addAll(failedRecords);
                        allProcessedRecords.addAll(processedRecords);
                        failedRecords.clear();
                        processedRecords.clear();
                        break; // do next eventData
                    } else if (ExecuteResult.RETRY == exeResult) {
                        retryCount = retryCount + 1;
                        processedRecords.clear();
                        failedRecords.clear();
                        failedRecords.addAll(splitDatas);
                        int retry = 3;
                        if (retryCount >= retry) {
                            processFailedDatas(index);
                            throw new RuntimeException(String.format("execute retry %s times failed", retryCount), error);
                        } else {
                            try {
                                int retryWait = 3000;
                                int wait = retryCount * retryWait;
                                wait = Math.max(wait, retryWait);
                                Thread.sleep(wait);
                            } catch (InterruptedException ex) {
                                Thread.interrupted();
                                processFailedDatas(index);
                                throw new RuntimeException(ex);
                            }
                        }
                    } else {
                        processedRecords.clear();
                        failedRecords.clear();
                        failedRecords.addAll(splitDatas);
                        processFailedDatas(index);
                        throw error;
                    }
                }
            }
            context.getFailedRecords().addAll(allFailedRecords);
            context.getProcessedRecords().addAll(allProcessedRecords);
            return null;
        }

        private void doPreparedStatement(PreparedStatement ps, DbDialect dbDialect, LobCreator lobCreator,
                                         CanalConnectRecord record) throws SQLException {
            EventType type = record.getEventType();
            List<EventColumn> columns = new ArrayList<EventColumn>();
            if (type.isInsert()) {
                columns.addAll(record.getColumns());
                columns.addAll(record.getKeys());
            } else if (type.isDelete()) {
                columns.addAll(record.getKeys());
            } else if (type.isUpdate()) {
                boolean existOldKeys = !CollectionUtils.isEmpty(record.getOldKeys());
                columns.addAll(record.getUpdatedColumns());
                if (existOldKeys && dbDialect.isDRDS()) {
                    columns.addAll(record.getUpdatedKeys());
                } else {
                    columns.addAll(record.getKeys());
                }
                if (existOldKeys) {
                    columns.addAll(record.getOldKeys());
                }
            }

            for (int i = 0; i < columns.size(); i++) {
                int paramIndex = i + 1;
                EventColumn column = columns.get(i);
                int sqlType = column.getColumnType();

                Object param = null;
                if (dbDialect instanceof MysqlDialect
                    && (sqlType == Types.TIME || sqlType == Types.TIMESTAMP || sqlType == Types.DATE)) {
                    param = column.getColumnValue();
                } else {
                    param = SqlUtils.stringToSqlValue(column.getColumnValue(),
                        sqlType,
                        false,
                        dbDialect.isEmptyStringNulled());
                }

                try {
                    switch (sqlType) {
                        case Types.CLOB:
                            lobCreator.setClobAsString(ps, paramIndex, (String) param);
                            break;

                        case Types.BLOB:
                            lobCreator.setBlobAsBytes(ps, paramIndex, (byte[]) param);
                            break;
                        case Types.TIME:
                        case Types.TIMESTAMP:
                        case Types.DATE:
                            if (dbDialect instanceof MysqlDialect) {
                                ps.setObject(paramIndex, param);
                            } else {
                                StatementCreatorUtils.setParameterValue(ps, paramIndex, sqlType, null, param);
                            }
                            break;
                        case Types.BIT:
                            if (dbDialect instanceof MysqlDialect) {
                                StatementCreatorUtils.setParameterValue(ps, paramIndex, Types.DECIMAL, null, param);
                            } else {
                                StatementCreatorUtils.setParameterValue(ps, paramIndex, sqlType, null, param);
                            }
                            break;
                        default:
                            StatementCreatorUtils.setParameterValue(ps, paramIndex, sqlType, null, param);
                            break;
                    }
                } catch (SQLException ex) {
                    log.error("## SetParam error , [pairId={}, sqltype={}, value={}]",
                        record.getPairId(), sqlType, param);
                    throw ex;
                }
            }
        }

        private void processStat(CanalConnectRecord record, int affect, boolean batch) {
            if (batch && (affect < 1 && affect != Statement.SUCCESS_NO_INFO)) {
                failedRecords.add(record);
            } else if (!batch && affect < 1) {
                failedRecords.add(record);
            } else {
                processedRecords.add(record);
                // this.processStat(record, context);
            }
        }

        private void processFailedDatas(int index) {
            allFailedRecords.addAll(failedRecords);
            context.getFailedRecords().addAll(allFailedRecords);
            for (; index < records.size(); index++) {
                context.getFailedRecords().add(records.get(index));
            }
            allProcessedRecords.addAll(processedRecords);
            context.getProcessedRecords().addAll(allProcessedRecords);
        }
    }

}
