package org.apache.eventmesh.common.remote.offset.canal;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.eventmesh.common.config.connector.rdb.canal.JobRdbFullPosition;
import org.apache.eventmesh.common.remote.offset.RecordOffset;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString
public class CanalFullRecordOffset extends RecordOffset {
    private JobRdbFullPosition position;
    @Override
    public Class<? extends RecordOffset> getRecordOffsetClass() {
        return CanalFullRecordOffset.class;
    }
}
