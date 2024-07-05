package org.apache.eventmesh.common.config.connector.rdb.canal.mysql;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.eventmesh.common.config.connector.rdb.canal.CanalMySQLType;
import org.apache.eventmesh.common.config.connector.rdb.canal.RdbColumnDefinition;

@Data
@EqualsAndHashCode(callSuper = true)
public class MySQLColumnDef extends RdbColumnDefinition {
    private CanalMySQLType type;
}
