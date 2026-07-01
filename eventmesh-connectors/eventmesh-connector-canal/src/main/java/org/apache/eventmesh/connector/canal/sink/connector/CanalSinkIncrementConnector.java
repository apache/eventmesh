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
import org.apache.eventmesh.common.config.connector.rdb.canal.CanalSinkIncrementConfig;
import org.apache.eventmesh.common.utils.JsonUtils;
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
import org.apache.eventmesh.connector.canal.sink.GtidBatch;
import org.apache.eventmesh.connector.canal.sink.GtidBatchManager;
import org.apache.eventmesh.connector.canal.source.table.RdbTableMgr;
import org.apache.eventmesh.openconnect.api.ConnectorCreateService;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SinkConnectorContext;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.offsetmgmt.api.callback.SendExceptionContext;
import org.apache.eventmesh.openconnect.offsetmgmt.api.callback.SendResult;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.util.ConfigUtil;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.SerializationUtils;

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
import java.util.concurrent.Executors;
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
import com.fasterxml.jackson.core.type.TypeReference;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CanalSinkIncrementConnector implements Sink, ConnectorCreateService<Sink> {

    private CanalSinkIncrementConfig sinkConfig;

    private JdbcTemplate jdbcTemplate;

    private SqlBuilderLoadInterceptor interceptor;

    private DbDialect dbDialect;

    private ExecutorService executor;

    private ExecutorService gtidSingleExecutor;

    private int batchSize = 50;

    private boolean useBatch = true;

    private RdbTableMgr tableMgr;

    @Override
    public Class<? extends Config> configClass() {
        return CanalSinkIncrementConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
        // init config for canal source connector
        this.sinkConfig = (CanalSinkIncrementConfig) config;
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        // init config for canal source connector
        SinkConnectorContext sinkConnectorContext = (SinkConnectorContext) connectorContext;
        CanalSinkConfig canalSinkConfig = (CanalSinkConfig) sinkConnectorContext.getSinkConfig();
        this.sinkConfig = ConfigUtil.parse(canalSinkConfig.getSinkConfig(), CanalSinkIncrementConfig.class);
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
        gtidSingleExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "gtidSingleExecutor"));
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
    public void onException(ConnectRecord record) {

    }

    @Override
    public void stop() {
        executor.shutdown();
        gtidSingleExecutor.shutdown();
    }

    @Override
    public void put(List<ConnectRecord> sinkRecords) {
        DbLoadContext context = new DbLoadContext();
        for (ConnectRecord connectRecord : sinkRecords) {
            List<CanalConnectRecord> canalConnectRecordList = new ArrayList<>();

            List<CanalConnectRecord> canalConnectRecords = convertToCanalConnectRecord(connectRecord);

            // deep copy connectRecord data
            for (CanalConnectRecord record : canalConnectRecords) {
                canalConnectRecordList.add(SerializationUtils.clone(record));
            }
            canalConnectRecordList = filterRecord(canalConnectRecordList);
            if (isDdlDatas(canalConnectRecordList)) {
                doDdl(context, canalConnectRecordList, connectRecord);
            } else if (sinkConfig.isGTIDMode()) {
                doLoadWithGtid(context, sinkConfig, connectRecord);
            } else {
                canalConnectRecordList = DbLoadMerger.merge(canalConnectRecordList);

                DbLoadData loadData = new DbLoadData();
                doBefore(canalConnectRecordList, loadData);

                doLoad(context, sinkConfig, loadData, connectRecord);

            }

        }
    }

    @Override
    public Sink create() {
        return new CanalSinkIncrementConnector();
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

    private void doDdl(DbLoadContext context, List<CanalConnectRecord> canalConnectRecordList, ConnectRecord connectRecord) {
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
                connectRecord.getCallback().onException(buildSendExceptionContext(connectRecord, e));
                throw new RuntimeException(e);
            }
        }
        connectRecord.getCallback().onSuccess(convertToSendResult(connectRecord));
    }

    private SendExceptionContext buildSendExceptionContext(ConnectRecord record, Throwable e) {
        SendExceptionContext sendExceptionContext = new SendExceptionContext();
        sendExceptionContext.setMessageId(record.getRecordId());
        sendExceptionContext.setCause(e);
        if (org.apache.commons.lang3.StringUtils.isNotEmpty(record.getExtension("topic"))) {
            sendExceptionContext.setTopic(record.getExtension("topic"));
        }
        return sendExceptionContext;
    }

    private SendResult convertToSendResult(ConnectRecord record) {
        SendResult result = new SendResult();
        result.setMessageId(record.getRecordId());
        if (org.apache.commons.lang3.StringUtils.isNotEmpty(record.getExtension("topic"))) {
            result.setTopic(record.getExtension("topic"));
        }
        return result;
    }

    private void doBefore(List<CanalConnectRecord> canalConnectRecordList, final DbLoadData loadData) {
        for (final CanalConnectRecord record : canalConnectRecordList) {
            boolean filter = interceptor.before(sinkConfig, record);
            if (!filter) {
                loadData.merge(record);
            }
        }
    }

    private void doLoad(DbLoadContext context, CanalSinkIncrementConfig sinkConfig, DbLoadData loadData, ConnectRecord connectRecord) {
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

        doTwoPhase(context, sinkConfig, batchDatas, true, connectRecord);

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

        doTwoPhase(context, sinkConfig, batchDatas, true, connectRecord);

        batchDatas.clear();
    }

    private void doLoadWithGtid(DbLoadContext context, CanalSinkIncrementConfig sinkConfig, ConnectRecord connectRecord) {
        int batchIndex = connectRecord.getExtension("batchIndex", Integer.class);
        int totalBatches = connectRecord.getExtension("totalBatches", Integer.class);
        List<CanalConnectRecord> canalConnectRecordList = convertToCanalConnectRecord(connectRecord);

        String gtid = canalConnectRecordList.get(0).getCurrentGtid();
        GtidBatchManager.addBatch(gtid, batchIndex, totalBatches, canalConnectRecordList);
        // check whether the batch is complete
        if (GtidBatchManager.isComplete(gtid)) {
            GtidBatch batch = GtidBatchManager.getGtidBatch(gtid);
            List<List<CanalConnectRecord>> totalRows = batch.getBatches();
            List<CanalConnectRecord> filteredRows = new ArrayList<>();
            for (List<CanalConnectRecord> canalConnectRecords : totalRows) {
                canalConnectRecords = filterRecord(canalConnectRecords);
                if (!CollectionUtils.isEmpty(canalConnectRecords)) {
                    for (final CanalConnectRecord record : canalConnectRecords) {
                        boolean filter = interceptor.before(sinkConfig, record);
                        filteredRows.add(record);
                    }
                }
            }
            context.setGtid(gtid);
            Future<Exception> result = gtidSingleExecutor.submit(new DbLoadWorker(context, filteredRows, dbDialect, false, sinkConfig));
            Exception ex = null;
            try {
                ex = result.get();
                if (ex == null) {
                    connectRecord.getCallback().onSuccess(convertToSendResult(connectRecord));
                }
            } catch (Exception e) {
                ex = e;
            }
            Boolean skipException = sinkConfig.getSkipException();
            if (skipException != null && skipException) {
                if (ex != null) {
                    // do skip
                    log.warn("skip exception will ack data : {} , caused by {}",
                        filteredRows,
                        ExceptionUtils.getFullStackTrace(ex));
                    GtidBatchManager.removeGtidBatch(gtid);
                    connectRecord.getCallback().onSuccess(convertToSendResult(connectRecord));
                }
            } else {
                if (ex != null) {
                    log.error("sink connector will shutdown by " + ex.getMessage(), ExceptionUtils.getFullStackTrace(ex));
                    connectRecord.getCallback().onException(buildSendExceptionContext(connectRecord, ex));
                    gtidSingleExecutor.shutdown();
                    System.exit(1);
                } else {
                    GtidBatchManager.removeGtidBatch(gtid);
                }
            }
        } else {
            log.info("Batch received, waiting for other batches.");
            // ack this record
            connectRecord.getCallback().onSuccess(convertToSendResult(connectRecord));
        }
    }

    private List<CanalConnectRecord> convertToCanalConnectRecord(ConnectRecord connectRecord) {
        List<CanalConnectRecord> canalConnectRecordList;
        try {
            canalConnectRecordList =
                JsonUtils.parseTypeReferenceObject((byte[]) connectRecord.getData(), new TypeReference<List<CanalConnectRecord>>() {
                });
        } catch (Exception e) {
            log.error("Failed to parse the canalConnectRecords.", e);
            connectRecord.getCallback().onException(buildSendExceptionContext(connectRecord, e));
            throw new RuntimeException("Failed to parse the canalConnectRecords.", e);
        }
        return canalConnectRecordList;
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

    private void doTwoPhase(DbLoadContext context, CanalSinkIncrementConfig sinkConfig, List<List<CanalConnectRecord>> totalRows, boolean canBatch,
        ConnectRecord connectRecord) {
        List<Future<Exception>> results = new ArrayList<>();
        for (List<CanalConnectRecord> rows : totalRows) {
            if (CollectionUtils.isEmpty(rows)) {
                continue;
            }
            results.add(executor.submit(new DbLoadWorker(context, rows, dbDialect, canBatch, sinkConfig)));
        }

        boolean partFailed = false;
        for (Future<Exception> result : results) {
            Exception ex = null;
            try {
                ex = result.get();
                if (ex == null) {
                    connectRecord.getCallback().onSuccess(convertToSendResult(connectRecord));
                }
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
                    DbLoadWorker worker = new DbLoadWorker(context, Arrays.asList(retryRecord), dbDialect, false, sinkConfig);
                    try {
                        Exception ex = worker.call();
                        if (ex != null) {
                            // do skip
                            log.warn("skip exception for data : {} , caused by {}",
                                retryRecord,
                                ExceptionUtils.getFullStackTrace(ex));
                            connectRecord.getCallback().onSuccess(convertToSendResult(connectRecord));
                        }
                    } catch (Exception ex) {
                        // do skip
                        log.warn("skip exception for data : {} , caused by {}",
                            retryRecord,
                            ExceptionUtils.getFullStackTrace(ex));
                        connectRecord.getCallback().onSuccess(convertToSendResult(connectRecord));
                    }
                }
            } else {
                DbLoadWorker worker = new DbLoadWorker(context, retryRecords, dbDialect, false, sinkConfig);
                try {
                    Exception ex = worker.call();
                    if (ex != null) {
                        throw ex;
                    }
                } catch (Exception ex) {
                    log.error("##load phase two failed!", ex);
                    log.error("sink connector will shutdown by " + ex.getMessage(), ex);
                    connectRecord.getCallback().onException(buildSendExceptionContext(connectRecord, ex));
                    executor.shutdown();
                    System.exit(1);
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

        private final CanalSinkIncrementConfig sinkConfig;

        private final List<CanalConnectRecord> allFailedRecords = new ArrayList<>();
        private final List<CanalConnectRecord> allProcessedRecords = new ArrayList<>();
        private final List<CanalConnectRecord> processedRecords = new ArrayList<>();
        private final List<CanalConnectRecord> failedRecords = new ArrayList<>();

        public DbLoadWorker(DbLoadContext context, List<CanalConnectRecord> records, DbDialect dbDialect, boolean canBatch,
            CanalSinkIncrementConfig sinkConfig) {
            this.context = context;
            this.records = records;
            this.canBatch = canBatch;
            this.dbDialect = dbDialect;
            this.sinkConfig = sinkConfig;
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

            if (sinkConfig.isGTIDMode()) {
                int retryCount = 0;
                final List<CanalConnectRecord> toExecuteRecords = new ArrayList<>();
                try {
                    if (!CollectionUtils.isEmpty(failedRecords)) {
                        // if failedRecords not empty, make it retry
                        toExecuteRecords.addAll(failedRecords);
                    } else {
                        toExecuteRecords.addAll(records);
                        // add to failed record first, maybe get lob or datasource error
                        failedRecords.addAll(toExecuteRecords);
                    }
                    JdbcTemplate template = dbDialect.getJdbcTemplate();
                    String sourceGtid = context.getGtid();
                    if (StringUtils.isNotEmpty(sourceGtid) && !sinkConfig.isMariaDB()) {
                        String setMySQLGtid = "SET @@session.gtid_next = '" + sourceGtid + "';";
                        template.execute(setMySQLGtid);
                    } else if (StringUtils.isNotEmpty(sourceGtid) && sinkConfig.isMariaDB()) {
                        throw new RuntimeException("unsupport gtid mode for mariaDB");
                    } else {
                        log.error("gtid is empty in gtid mode");
                        throw new RuntimeException("gtid is empty in gtid mode");
                    }

                    final LobCreator lobCreator = dbDialect.getLobHandler().getLobCreator();
                    int affect = (Integer) dbDialect.getTransactionTemplate().execute((TransactionCallback) status -> {
                        try {
                            failedRecords.clear();
                            processedRecords.clear();
                            int affect1 = 0;
                            for (CanalConnectRecord record : toExecuteRecords) {
                                int affects = template.update(record.getSql(), new PreparedStatementSetter() {
                                    public void setValues(PreparedStatement ps) throws SQLException {
                                        doPreparedStatement(ps, dbDialect, lobCreator, record);
                                    }
                                });
                                affect1 = affect1 + affects;
                                processStat(record, affects, false);
                            }
                            return affect1;
                        } catch (Exception e) {
                            // rollback
                            status.setRollbackOnly();
                            throw new RuntimeException("Failed to executed", e);
                        } finally {
                            lobCreator.close();
                        }
                    });

                    // reset gtid
                    if (sinkConfig.isMariaDB()) {
                        throw new RuntimeException("unsupport gtid mode for mariaDB");
                    } else {
                        String resetMySQLGtid = "SET @@session.gtid_next = 'AUTOMATIC';";
                        dbDialect.getJdbcTemplate().execute(resetMySQLGtid);
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
                } else if (ExecuteResult.RETRY == exeResult) {
                    retryCount = retryCount + 1;
                    processedRecords.clear();
                    failedRecords.clear();
                    failedRecords.addAll(toExecuteRecords);
                    int retry = 3;
                    if (retryCount >= retry) {
                        processFailedDatas(toExecuteRecords.size());
                        throw new RuntimeException(String.format("execute retry %s times failed", retryCount), error);
                    } else {
                        try {
                            int retryWait = 3000;
                            int wait = retryCount * retryWait;
                            wait = Math.max(wait, retryWait);
                            Thread.sleep(wait);
                        } catch (InterruptedException ex) {
                            Thread.interrupted();
                            processFailedDatas(toExecuteRecords.size());
                            throw new RuntimeException(ex);
                        }
                    }
                } else {
                    processedRecords.clear();
                    failedRecords.clear();
                    failedRecords.addAll(toExecuteRecords);
                    processFailedDatas(toExecuteRecords.size());
                    throw error;
                }
            } else {
                int index = 0;
                while (index < records.size()) {
                    final List<CanalConnectRecord> toExecuteRecords = new ArrayList<>();
                    if (useBatch && canBatch) {
                        int end = Math.min(index + batchSize, records.size());
                        toExecuteRecords.addAll(records.subList(index, end));
                        index = end;
                    } else {
                        toExecuteRecords.add(records.get(index));
                        index = index + 1;
                    }

                    int retryCount = 0;
                    while (true) {
                        try {
                            if (!CollectionUtils.isEmpty(failedRecords)) {
                                toExecuteRecords.clear();
                                toExecuteRecords.addAll(failedRecords);
                            } else {
                                failedRecords.addAll(toExecuteRecords);
                            }

                            final LobCreator lobCreator = dbDialect.getLobHandler().getLobCreator();
                            if (useBatch && canBatch) {
                                JdbcTemplate template = dbDialect.getJdbcTemplate();
                                final String sql = toExecuteRecords.get(0).getSql();

                                int[] affects = new int[toExecuteRecords.size()];

                                affects = (int[]) dbDialect.getTransactionTemplate().execute((TransactionCallback) status -> {
                                    try {
                                        failedRecords.clear();
                                        processedRecords.clear();
                                        int[] affects1 = template.batchUpdate(sql, new BatchPreparedStatementSetter() {

                                            public void setValues(PreparedStatement ps, int idx) throws SQLException {
                                                doPreparedStatement(ps, dbDialect, lobCreator, toExecuteRecords.get(idx));
                                            }

                                            public int getBatchSize() {
                                                return toExecuteRecords.size();
                                            }
                                        });
                                        return affects1;
                                    } catch (Exception e) {
                                        // rollback
                                        status.setRollbackOnly();
                                        throw new RuntimeException("Failed to execute batch ", e);
                                    } finally {
                                        lobCreator.close();
                                    }
                                });

                                for (int i = 0; i < toExecuteRecords.size(); i++) {
                                    assert affects != null;
                                    processStat(toExecuteRecords.get(i), affects[i], true);
                                }
                            } else {
                                final CanalConnectRecord record = toExecuteRecords.get(0);
                                JdbcTemplate template = dbDialect.getJdbcTemplate();
                                int affect = 0;
                                affect = (Integer) dbDialect.getTransactionTemplate().execute((TransactionCallback) status -> {
                                    try {
                                        failedRecords.clear();
                                        processedRecords.clear();
                                        int affect1 = template.update(record.getSql(), new PreparedStatementSetter() {

                                            public void setValues(PreparedStatement ps) throws SQLException {
                                                doPreparedStatement(ps, dbDialect, lobCreator, record);
                                            }
                                        });
                                        return affect1;
                                    } catch (Exception e) {
                                        // rollback
                                        status.setRollbackOnly();
                                        throw new RuntimeException("Failed to executed", e);
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
                            failedRecords.addAll(toExecuteRecords);
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
                            failedRecords.addAll(toExecuteRecords);
                            processFailedDatas(index);
                            throw error;
                        }
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
                columns.addAll(record.getKeys());
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
