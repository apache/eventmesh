package org.apache.eventmesh.common.config.connector.rdb.canal;

import lombok.Data;
import lombok.ToString;

import java.math.BigDecimal;

@Data
@ToString
public class JobRdbFullPosition {
    private String jobId;
    private String schema;
    private String tableName;
    private String primaryKeyRecords;
    private long maxCount;
    private boolean finished;
    private BigDecimal percent;
}
