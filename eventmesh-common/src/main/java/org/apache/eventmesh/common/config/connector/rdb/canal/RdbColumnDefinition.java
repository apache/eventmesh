package org.apache.eventmesh.common.config.connector.rdb.canal;

import lombok.Data;

import java.sql.JDBCType;

@Data
public class RdbColumnDefinition {
    protected String name;
    protected JDBCType jdbcType;
}
