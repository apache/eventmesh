package org.apache.eventmesh.connector.wecom.connector;

import org.apache.eventmesh.common.remote.offset.RecordOffset;

public class MockRecordOffset extends RecordOffset {
    @Override
    public Class<? extends RecordOffset> getRecordOffsetClass() {
        return MockRecordOffset.class;
    }
}
