package org.apache.eventmesh.common.config.connector.rdb.canal;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RdbFullPosition {
    private String jobId;
    private String schema;
    private String tableName;
    private String curPrimaryKey;
    private BigDecimal minPrimaryKeyNum;
    private BigDecimal maxPrimaryKeyNum;
    private boolean finished;
}
