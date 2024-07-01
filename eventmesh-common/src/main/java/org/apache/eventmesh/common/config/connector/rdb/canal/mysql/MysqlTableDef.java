package org.apache.eventmesh.common.config.connector.rdb.canal.mysql;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.eventmesh.common.config.connector.rdb.canal.RdbTableDefinition;

import java.util.Set;

/**
 * Description:
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MysqlTableDef extends RdbTableDefinition {
    private Set<String> colNames;
    private Set<String> primaryKeys;
}
