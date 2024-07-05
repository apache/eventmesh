package org.apache.eventmesh.common.config.connector.rdb.canal.mysql;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.eventmesh.common.config.connector.rdb.canal.RdbColumnDefinition;
import org.apache.eventmesh.common.config.connector.rdb.canal.RdbTableDefinition;

import java.util.Map;
import java.util.Set;

/**
 * Description:
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MySQLTableDef extends RdbTableDefinition {
    private Set<String> primaryKeys;
    private Map<String, RdbColumnDefinition> columnDefinitions;
}
