package org.apache.eventmesh.connector.canal.source.position;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.common.AbstractComponent;
import org.apache.eventmesh.common.config.connector.rdb.canal.CanalSourceFullConfig;
import org.apache.eventmesh.common.config.connector.rdb.canal.RdbDBDefinition;
import org.apache.eventmesh.common.config.connector.rdb.canal.RdbFullPosition;
import org.apache.eventmesh.common.config.connector.rdb.canal.RdbTableDefinition;
import org.apache.eventmesh.common.remote.offset.RecordPosition;
import org.apache.eventmesh.connector.canal.source.table.RdbSimpleTable;

import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;

@AllArgsConstructor
@Slf4j
public class CanalFullPositionMgr extends AbstractComponent {
    private CanalSourceFullConfig config;
    private ScheduledThreadPoolExecutor executor;
    private Map<RdbSimpleTable, RdbFullPosition> positions;

    @Override
    protected void startup() throws Exception {
        if (config == null || config.getConnectorConfig() == null || config.getConnectorConfig().getDatabases() == null) {
            log.info("config or database is null");
            return;
        }
        executor = new ScheduledThreadPoolExecutor(1, r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            thread.setName("task-full-position-timer");
            return thread;
        }, new ScheduledThreadPoolExecutor.DiscardOldestPolicy());
        if (config.getStartPosition() != null) {
            for (RecordPosition recordPosition : config.getStartPosition()) {

            }
        }

    }

    private void processPositions(CanalSourceFullConfig config) {
        for (RdbDBDefinition database : config.getConnectorConfig().getDatabases()) {
            for (RdbTableDefinition table : database.getTables()) {
                log.info("init position of data [{}] table [{}]", database.getSchema(), table.getTableName());
                RdbSimpleTable simpleTable = new RdbSimpleTable(database.getSchema(), table.getTableName());
                RdbFullPosition recordPosition = positions.get(simpleTable);
                if (recordPosition == null || !recordPosition.isFinished()) {
                    positions.put(simpleTable,initPosition(config, database.getSchema(), table.getTableName(), recordPosition));
                }
            }
        }
    }

    private RdbFullPosition initPosition(CanalSourceFullConfig config, String db, String table,
                                         RdbFullPosition recordPosition) {
        return null;
    }

    @Override
    protected void shutdown() throws Exception {

    }
}
