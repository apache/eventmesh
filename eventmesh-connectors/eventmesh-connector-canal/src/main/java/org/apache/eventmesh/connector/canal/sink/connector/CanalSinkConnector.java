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
        this.batchSize = sinkConfig.getBatchsize();
        this.useBatch = sinkConfig.getUseBatch();
        DatabaseConnection.sinkConfig = this.sinkConfig;
        DatabaseConnection.initSinkConnection();
        jdbcTemplate = new JdbcTemplate(DatabaseConnection.sinkDataSource);
        dbDialect = new MysqlDialect(jdbcTemplate, new DefaultLobHandler());
        interceptor = new SqlBuilderLoadInterceptor();
        interceptor.setDbDialect(dbDialect);
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
            canalConnectRecordList = filterRecord(canalConnectRecordList, sinkConfig);
            if (isDdlDatas(canalConnectRecordList)) {
                doDdl(context, canalConnectRecordList);
            } else {
                // 进行一次数据合并，合并相同pk的多次I/U/D操作
                canalConnectRecordList = DbLoadMerger.merge(canalConnectRecordList);
                // 按I/U/D进行归并处理
                DbLoadData loadData = new DbLoadData();
                doBefore(canalConnectRecordList, loadData);
                // 执行load操作
                doLoad(context, sinkConfig, loadData);

            }

        }
    }

    @Override
    public Sink create() {
        return new CanalSinkConnector();
    }

    /**
     * 分析整个数据，将datas划分为多个批次. ddl sql前的DML并发执行，然后串行执行ddl后，再并发执行DML
     *
     * @return
     */
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

    /**
     * 过滤掉不需要处理的数据
     */
    private List<CanalConnectRecord> filterRecord(List<CanalConnectRecord> canalConnectRecordList, CanalSinkConfig sinkConfig) {
        return canalConnectRecordList.stream()
            .filter(record -> sinkConfig.getSinkConnectorConfig().getSchemaName().equalsIgnoreCase(record.getSchemaName()) &&
                sinkConfig.getSinkConnectorConfig().getTableName().equalsIgnoreCase(record.getTableName()))
            .collect(Collectors.toList());
    }

    /**
     * 执行ddl的调用，处理逻辑比较简单: 串行调用
     */
    private void doDdl(DbLoadContext context, List<CanalConnectRecord> canalConnectRecordList) {
        for (final CanalConnectRecord record : canalConnectRecordList) {
            try {
                Boolean result = jdbcTemplate.execute(new StatementCallback<Boolean>() {

                    public Boolean doInStatement(Statement stmt) throws SQLException, DataAccessException {
                        boolean result = true;
                        if (StringUtils.isNotEmpty(record.getDdlSchemaName())) {
                            // 如果mysql，执行ddl时，切换到在源库执行的schema上
                            // result &= stmt.execute("use " +
                            // data.getDdlSchemaName());

                            // 解决当数据库名称为关键字如"Order"的时候,会报错,无法同步
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

    /**
     * 执行数据处理，比如数据冲突检测
     */
    private void doBefore(List<CanalConnectRecord> canalConnectRecordList, final DbLoadData loadData) {
        for (final CanalConnectRecord record : canalConnectRecordList) {
            boolean filter = interceptor.before(sinkConfig, record);
            if (!filter) {
                loadData.merge(record);// 进行分类
            }
        }
    }

    private void doLoad(DbLoadContext context, CanalSinkConfig sinkConfig, DbLoadData loadData) {
        // 优先处理delete,可以利用batch优化
        List<List<CanalConnectRecord>> batchDatas = new ArrayList<>();
        for (TableLoadData tableData : loadData.getTables()) {
            if (useBatch) {
                // 优先执行delete语句，针对unique更新，一般会进行delete + insert的处理模式，避免并发更新
                batchDatas.addAll(split(tableData.getDeleteDatas()));
            } else {
                // 如果不可以执行batch，则按照单条数据进行并行提交
                // 优先执行delete语句，针对unique更新，一般会进行delete + insert的处理模式，避免并发更新
                for (CanalConnectRecord data : tableData.getDeleteDatas()) {
                    batchDatas.add(Arrays.asList(data));
                }
            }
        }

        doTwoPhase(context, sinkConfig, batchDatas, true);

        batchDatas.clear();

        // 处理下insert/update
        for (TableLoadData tableData : loadData.getTables()) {
            if (useBatch) {
                // 执行insert + update语句
                batchDatas.addAll(split(tableData.getInsertDatas()));
                batchDatas.addAll(split(tableData.getUpdateDatas()));// 每条记录分为一组，并行加载
            } else {
                // 执行insert + update语句
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

    /**
     * 将对应的数据按照sql相同进行batch组合
     */
    private List<List<CanalConnectRecord>> split(List<CanalConnectRecord> records) {
        List<List<CanalConnectRecord>> result = new ArrayList<>();
        if (records == null || records.isEmpty()) {
            return result;
        } else {
            int[] bits = new int[records.size()];// 初始化一个标记，用于标明对应的记录是否已分入某个batch
            for (int i = 0; i < bits.length; i++) {
                // 跳过已经被分入batch的
                while (i < bits.length && bits[i] == 1) {
                    i++;
                }

                if (i >= bits.length) { // 已处理完成，退出
                    break;
                }

                // 开始添加batch，最大只加入batchSize个数的对象
                List<CanalConnectRecord> batch = new ArrayList<>();
                bits[i] = 1;
                batch.add(records.get(i));
                for (int j = i + 1; j < bits.length && batch.size() < batchSize; j++) {
                    if (bits[j] == 0 && canBatch(records.get(i), records.get(j))) {
                        batch.add(records.get(j));
                        bits[j] = 1;// 修改为已加入
                    }
                }
                result.add(batch);
            }

            return result;
        }
    }

    /**
     * 判断两条记录是否可以作为一个batch提交，主要判断sql是否相等. 可优先通过schemaName进行判断
     */
    private boolean canBatch(CanalConnectRecord source, CanalConnectRecord target) {
        return StringUtils.equals(source.getSchemaName(),
            target.getSchemaName())
            && StringUtils.equals(source.getTableName(), target.getTableName())
            && StringUtils.equals(source.getSql(), target.getSql());
    }

    /**
     * 首先进行并行执行，出错后转为串行执行
     */
    private void doTwoPhase(DbLoadContext context, CanalSinkConfig sinkConfig, List<List<CanalConnectRecord>> totalRows, boolean canBatch) {
        // 预处理下数据
        List<Future<Exception>> results = new ArrayList<Future<Exception>>();
        for (List<CanalConnectRecord> rows : totalRows) {
            if (CollectionUtils.isEmpty(rows)) {
                continue; // 过滤空记录
            }
            results.add(executor.submit(new DbLoadWorker(context, rows, dbDialect, canBatch)));
        }

        boolean partFailed = false;
        for (Future<Exception> result : results) {
            Exception ex = null;
            try {
                ex = result.get();
//                for (CanalConnectRecord data : totalRows.get(i)) {
//                    interceptor.after(context, data);// 通知加载完成
//                }
            } catch (Exception e) {
                ex = e;
            }

            if (ex != null) {
                log.warn("##load phase one failed!", ex);
                partFailed = true;
            }
        }

        if (partFailed) {

            // 尝试的内容换成phase one跑的所有数据，避免因failed datas计算错误而导致丢数据
            List<CanalConnectRecord> retryRecords = new ArrayList<>();
            for (List<CanalConnectRecord> rows : totalRows) {
                retryRecords.addAll(rows);
            }

            context.getFailedRecords().clear(); // 清理failed data数据

            // 可能为null，manager老版本数据序列化传输时，因为数据库中没有skipLoadException变量配置
            Boolean skipException = sinkConfig.getSkipException();
            if (skipException != null && skipException) {// 如果设置为允许跳过单条异常，则一条条执行数据load，准确过滤掉出错的记录，并进行日志记录
                for (CanalConnectRecord retryRecord : retryRecords) {
                    DbLoadWorker worker = new DbLoadWorker(context, Arrays.asList(retryRecord), dbDialect, false);// 强制设置batch为false
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
                // 直接一批进行处理，减少线程调度
                DbLoadWorker worker = new DbLoadWorker(context, retryRecords, dbDialect, false);// 强制设置batch为false
                try {
                    Exception ex = worker.call();
                    if (ex != null) {
                        throw ex; // 自己抛自己接
                    }
                } catch (Exception ex) {
                    log.error("##load phase two failed!", ex);
                    throw new RuntimeException(ex);
                }
            }

            // 清理failed data数据
//            for (CanalConnectRecord retryRecord : retryRecords) {
//                interceptor.after(context, retryRecord);// 通知加载完成
//            }
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
            int index = 0;// 记录下处理成功的记录下标
            while (index < records.size()) {
                // 处理数据切分
                final List<CanalConnectRecord> splitDatas = new ArrayList<>();
                if (useBatch && canBatch) {
                    int end = Math.min(index + batchSize, records.size());
                    splitDatas.addAll(records.subList(index, end));
                    index = end;// 移动到下一批次
                } else {
                    splitDatas.add(records.get(index));
                    index = index + 1;// 移动到下一条
                }

                int retryCount = 0;
                while (true) {
                    try {
                        if (!CollectionUtils.isEmpty(failedRecords)) {
                            splitDatas.clear();
                            splitDatas.addAll(failedRecords); // 下次重试时，只处理错误的记录
                        } else {
                            failedRecords.addAll(splitDatas); // 先添加为出错记录，可能获取lob,datasource会出错
                        }

                        final LobCreator lobCreator = dbDialect.getLobHandler().getLobCreator();
                        if (useBatch && canBatch) {
                            // 处理batch
                            final String sql = splitDatas.get(0).getSql();
                            int[] affects = new int[splitDatas.size()];
                            affects = (int[]) dbDialect.getTransactionTemplate().execute((TransactionCallback) status -> {
                                // 初始化一下内容
                                try {
                                    failedRecords.clear(); // 先清理
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

                            // 更新统计信息
                            for (int i = 0; i < splitDatas.size(); i++) {
                                assert affects != null;
                                processStat(splitDatas.get(i), affects[i], true);
                            }
                        } else {
                            final CanalConnectRecord record = splitDatas.get(0);// 直接取第一条
                            int affect = 0;
                            affect = (Integer) dbDialect.getTransactionTemplate().execute((TransactionCallback) status -> {
                                try {
                                    failedRecords.clear(); // 先清理
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
                            // 更新统计信息
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
                        allFailedRecords.addAll(failedRecords);// 记录一下异常到all记录中
                        allProcessedRecords.addAll(processedRecords);
                        failedRecords.clear();// 清空上一轮的处理
                        processedRecords.clear();
                        break; // do next eventData
                    } else if (ExecuteResult.RETRY == exeResult) {
                        retryCount = retryCount + 1;// 计数一次
                        // 出现异常，理论上当前的批次都会失败
                        processedRecords.clear();
                        failedRecords.clear();
                        failedRecords.addAll(splitDatas);
                        int retry = 3;
                        if (retryCount >= retry) {
                            processFailedDatas(index);// 重试已结束，添加出错记录并退出
                            throw new RuntimeException(String.format("execute retry %s times failed", retryCount), error);
                        } else {
                            try {
                                int retryWait = 3000;
                                int wait = retryCount * retryWait;
                                wait = Math.max(wait, retryWait);
                                Thread.sleep(wait);
                            } catch (InterruptedException ex) {
                                Thread.interrupted();
                                processFailedDatas(index);// 局部处理出错了
                                throw new RuntimeException(ex);
                            }
                        }
                    } else {
                        // 出现异常，理论上当前的批次都会失败
                        processedRecords.clear();
                        failedRecords.clear();
                        failedRecords.addAll(splitDatas);
                        processFailedDatas(index);// 局部处理出错了
                        throw error;
                    }
                }
            }

            // 记录一下当前处理过程中失败的记录,affect = 0的记录
            context.getFailedRecords().addAll(allFailedRecords);
            context.getProcessedRecords().addAll(allProcessedRecords);
            return null;
        }

        private void doPreparedStatement(PreparedStatement ps, DbDialect dbDialect, LobCreator lobCreator,
            CanalConnectRecord record) throws SQLException {
            EventType type = record.getEventType();
            // 注意insert/update语句对应的字段数序都是将主键排在后面
            List<EventColumn> columns = new ArrayList<EventColumn>();
            if (type.isInsert()) {
                columns.addAll(record.getColumns()); // insert为所有字段
                columns.addAll(record.getKeys());
            } else if (type.isDelete()) {
                columns.addAll(record.getKeys());
            } else if (type.isUpdate()) {
                boolean existOldKeys = !CollectionUtils.isEmpty(record.getOldKeys());
                columns.addAll(record.getUpdatedColumns());// 只更新带有isUpdate=true的字段
                if (existOldKeys && dbDialect.isDRDS()) {
                    // DRDS需要区分主键是否有变更
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
                    // 解决mysql的0000-00-00 00:00:00问题，直接依赖mysql
                    // driver进行处理，如果转化为Timestamp会出错
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
                            // 只处理mysql的时间类型，oracle的进行转化处理
                            if (dbDialect instanceof MysqlDialect) {
                                // 解决mysql的0000-00-00 00:00:00问题，直接依赖mysql
                                // driver进行处理，如果转化为Timestamp会出错
                                ps.setObject(paramIndex, param);
                            } else {
                                StatementCreatorUtils.setParameterValue(ps, paramIndex, sqlType, null, param);
                            }
                            break;
                        case Types.BIT:
                            // 只处理mysql的bit类型，bit最多存储64位，所以需要使用BigInteger进行处理才能不丢精度
                            // mysql driver将bit按照setInt进行处理，会导致数据越界
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
                failedRecords.add(record); // 记录到错误的临时队列，进行重试处理
            } else if (!batch && affect < 1) {
                failedRecords.add(record);// 记录到错误的临时队列，进行重试处理
            } else {
                processedRecords.add(record); // 记录到成功的临时队列，commit也可能会失败。所以这记录也可能需要进行重试
//              this.processStat(record, context);
            }
        }

        // 出现异常回滚了，记录一下异常记录
        private void processFailedDatas(int index) {
            allFailedRecords.addAll(failedRecords);// 添加失败记录
            context.getFailedRecords().addAll(allFailedRecords);// 添加历史出错记录
            for (; index < records.size(); index++) { // 记录一下未处理的数据
                context.getFailedRecords().add(records.get(index));
            }
            // 这里不需要添加当前成功记录，出现异常后会rollback所有的成功记录，比如processDatas有记录，但在commit出现失败
            // (bugfix)
            allProcessedRecords.addAll(processedRecords);
            context.getProcessedRecords().addAll(allProcessedRecords);// 添加历史成功记录
        }
    }

}
