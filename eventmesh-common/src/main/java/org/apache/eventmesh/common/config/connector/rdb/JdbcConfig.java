package org.apache.eventmesh.common.config.connector.rdb;

import org.apache.eventmesh.common.config.connector.rdb.canal.RdbDBDefinition;

import java.util.Set;

import lombok.Data;

@Data
public class JdbcConfig {
    private String url;

    private String dbAddress;

    private int dbPort;

    private String userName;

    private String passWord;

    private Set<RdbDBDefinition> databases;
}
