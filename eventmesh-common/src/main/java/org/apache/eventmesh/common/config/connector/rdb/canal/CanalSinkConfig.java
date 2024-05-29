package org.apache.eventmesh.common.config.connector.rdb.canal;

import org.apache.eventmesh.common.config.connector.SinkConfig;
import org.apache.eventmesh.common.remote.job.SyncMode;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CanalSinkConfig extends SinkConfig {

    private Integer batchsize = 50;                          // 批次大小

    private Boolean useBatch = true;                        // 是否使用batch模式

    private Integer poolSize = 5;                           // sink模块载入线程数，针对单个载入通道

    private SyncMode syncMode;                              // 同步模式：字段/整条记录

    private Boolean skipException = false;                  // 是否跳过sink执行异常

    public SinkConnectorConfig sinkConnectorConfig;

}
