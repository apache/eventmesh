package org.apache.eventmesh.connector.wecom.connector;

import org.apache.eventmesh.common.remote.offset.RecordPartition;

public class MockRecordPartition extends RecordPartition {
    @Override
    public Class<? extends RecordPartition> getRecordPartitionClass() {
        return MockRecordPartition.class;
    }
}
