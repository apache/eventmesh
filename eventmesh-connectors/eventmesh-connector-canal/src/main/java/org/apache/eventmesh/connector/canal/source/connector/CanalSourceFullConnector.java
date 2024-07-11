package org.apache.eventmesh.connector.canal.source.connector;

import org.apache.eventmesh.common.AbstractComponent;
import org.apache.eventmesh.common.EventMeshThreadFactory;
import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.config.connector.rdb.canal.CanalSourceFullConfig;
import org.apache.eventmesh.common.config.connector.rdb.canal.JobRdbFullPosition;
import org.apache.eventmesh.common.config.connector.rdb.canal.RdbDBDefinition;
import org.apache.eventmesh.common.config.connector.rdb.canal.RdbTableDefinition;
import org.apache.eventmesh.common.config.connector.rdb.canal.mysql.MySQLTableDef;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.connector.canal.DatabaseConnection;
import org.apache.eventmesh.connector.canal.source.CanalFullProducer;
import org.apache.eventmesh.connector.canal.source.position.CanalFullPositionMgr;
import org.apache.eventmesh.connector.canal.source.position.TableFullPosition;
import org.apache.eventmesh.connector.canal.source.table.RdbSimpleTable;
import org.apache.eventmesh.connector.canal.source.table.RdbTableMgr;
import org.apache.eventmesh.openconnect.api.ConnectorCreateService;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SourceConnectorContext;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CanalSourceFullConnector extends AbstractComponent implements Source, ConnectorCreateService<Source> {
    private CanalSourceFullConfig config;
    private CanalFullPositionMgr positionMgr;
    private RdbTableMgr tableMgr;
    private ThreadPoolExecutor executor;
    private final Map<String, DataSource> dataSources = new HashMap<>();
    private final BlockingQueue<List<ConnectRecord>> queue = new LinkedBlockingQueue<>();
    private final AtomicBoolean flag = new AtomicBoolean(true);

    @Override
    protected void startup() throws Exception {
        this.tableMgr.start();
        this.positionMgr.start();
        if (positionMgr.isFinished()) {
            log.info("connector [{}] has finished the job", config.getConnectorConfig().getConnectorName());
            return;
        }
        executor = new ThreadPoolExecutor(config.getParallel(), config.getParallel(), 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(), new EventMeshThreadFactory("canal-source-full"));
        List<CanalFullProducer> producers = new LinkedList<>();
        if (config.getConnectorConfig().getDatabases() != null) {
            for (RdbDBDefinition db : config.getConnectorConfig().getDatabases()) {
                for (RdbTableDefinition table : db.getTables()) {
                    try {
                        log.info("it will create producer of db [{}] table [{}]", db.getSchemaName(), table.getTableName());
                        DataSource dataSource = dataSources.computeIfAbsent(db.getSchemaName(),
                            k -> DatabaseConnection.createDruidDataSource(config.getConnectorConfig().getUrl(),
                                config.getConnectorConfig().getUserName(),
                                config.getConnectorConfig().getPassWord()));
                        RdbSimpleTable simpleTable = new RdbSimpleTable(db.getSchemaName(), table.getTableName());
                        JobRdbFullPosition position = positionMgr.getPosition(simpleTable);
                        if (position == null) {
                            throw new EventMeshException(String.format("db [%s] table [%s] have none position info",
                                db.getSchemaName(), table.getTableName()));
                        }
                        RdbTableDefinition tableDefinition = tableMgr.getTable(simpleTable);
                        if (tableDefinition == null) {
                            throw new EventMeshException(String.format("db [%s] table [%s] have none table definition info",
                                db.getSchemaName(), table.getTableName()));
                        }

                        producers.add(new CanalFullProducer(queue, dataSource, (MySQLTableDef) tableDefinition,
                            JsonUtils.parseObject(position.getPrimaryKeyRecords(), TableFullPosition.class),
                            config.getFlushSize()));
                    } catch (Exception e) {
                        log.error("create schema [{}] table [{}] producers fail", db.getSchemaName(),
                            table.getTableName(), e);
                    }
                }
            }
        }
        producers.forEach(p -> executor.execute(() -> p.start(flag)));
    }

    @Override
    protected void shutdown() throws Exception {
        flag.set(false);
    }

    @Override
    public Source create() {
        return new CanalSourceFullConnector();
    }

    @Override
    public Class<? extends Config> configClass() {
        return CanalSourceFullConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
        this.config = (CanalSourceFullConfig) config;
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        SourceConnectorContext sourceConnectorContext = (SourceConnectorContext) connectorContext;
        this.config = (CanalSourceFullConfig) sourceConnectorContext.getSourceConfig();
        this.tableMgr = new RdbTableMgr(config.getConnectorConfig());
        this.positionMgr = new CanalFullPositionMgr(config, tableMgr);
    }

    @Override
    public void commit(ConnectRecord record) {
        // nothing
    }

    @Override
    public String name() {
        return this.config.getConnectorConfig().getConnectorName();
    }

    @Override
    public List<ConnectRecord> poll() {
        while (flag.get()) {
            try {
                List<ConnectRecord> records = queue.poll(5, TimeUnit.SECONDS);
                if (records == null || records.isEmpty()) {
                    continue;
                }
                return records;
            } catch (InterruptedException ignore) {
                Thread.currentThread().interrupt();
                log.info("[{}] thread interrupted", this.getClass());
                return null;
            }
        }
        log.info("[{}] life flag is stop, so return null", this.getClass());
        return null;
    }

}
