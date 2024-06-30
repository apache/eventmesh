package org.apache.eventmesh.common.config.connector.rdb.canal;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.common.AbstractComponent;

import java.util.HashMap;
import java.util.Map;

/**
 * @Description:
 */
@Slf4j
@AllArgsConstructor
public class RdbTableMgr extends AbstractComponent {
    private final SourceConnectorConfig config;
    private final Map<String, RdbTableDefinition> tables = new HashMap<>();

    @Override
    protected void startup() throws Exception {
        if (config != null) {

        }
    }

    @Override
    protected void shutdown() throws Exception {

    }
}
