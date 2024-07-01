package org.apache.eventmesh.common.config.connector.rdb.canal;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.eventmesh.common.config.connector.SourceConfig;
import org.apache.eventmesh.common.remote.offset.RecordPosition;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class CanalSourceFullConfig extends SourceConfig {
    private SourceConnectorConfig connectorConfig;
    private List<RecordPosition> startPosition;
}
