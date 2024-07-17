package org.apache.eventmesh.common.config.connector.rdb.canal.mysql;

import org.apache.eventmesh.common.config.connector.rdb.canal.RdbTableDefinition;

import java.util.Map;
import java.util.Set;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Description:
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MySQLTableDef extends RdbTableDefinition {
    private Set<String> primaryKeys;
    private Map<String, MySQLColumnDef> columnDefinitions;
}
