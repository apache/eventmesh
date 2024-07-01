package org.apache.eventmesh.connector.canal.source.table;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.common.AbstractComponent;
import org.apache.eventmesh.common.config.connector.rdb.canal.RdbDBDefinition;
import org.apache.eventmesh.common.config.connector.rdb.canal.RdbTableDefinition;
import org.apache.eventmesh.common.config.connector.rdb.canal.SourceConnectorConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Description:
 */
@Slf4j
@AllArgsConstructor
public class RdbTableMgr extends AbstractComponent {
    private SourceConnectorConfig config;
    private final Map<String, RdbTableDefinition> tables = new HashMap<>();

    public static String generate(String ...params) {
        return String.join("@", params);
    }

    private RdbTableMgr(){
    }

    private static class RdbTableMgrHolder {
        private static final RdbTableMgr INSTANCE = new RdbTableMgr();
    }

    public static RdbTableMgr getInstance() {
        return RdbTableMgrHolder.INSTANCE;
    }

    public void init(SourceConnectorConfig config) {
        this.config = config;
    }

    public RdbTableDefinition getTable(String dbName, String tableName) {
        return tables.get(generate(dbName, tableName));
    }

    @Override
    protected void startup() throws Exception {
        if (config != null && config.getDatabases() != null) {
            for (RdbDBDefinition db : config.getDatabases()) {
                for (RdbTableDefinition table : db.getTables()) {
                    tables.put(generate(db.getSchema(), table.getTableName()), table);
                }
            }
        }
    }

    @Override
    protected void shutdown() throws Exception {

    }
}
