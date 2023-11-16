package org.apache.eventmesh.connector.openfunction.source.connector;

import org.apache.eventmesh.connector.openfunction.source.config.OpenFunctionSourceConfig;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.RecordOffset;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.RecordPartition;

import java.util.List;
import java.util.concurrent.BlockingQueue;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class OpenFunctionSourceConnectorTest {

    @Spy
    private OpenFunctionSourceConnector connector;


    @Test
    public void testSpringSourceConnector() throws Exception {
        OpenFunctionSourceConfig sourceConfig = new OpenFunctionSourceConfig();
        connector.init(sourceConfig);
        connector.start();
        final int count = 5;
        final String message = "testMessage";
        writeMockedRecords(count, message);
        List<ConnectRecord> connectRecords = connector.poll();
        Assertions.assertEquals(count, connectRecords.size());
        for (int i = 0; i < connectRecords.size(); i++) {
            Object actualMessage = String.valueOf(connectRecords.get(i).getData());
            String expectedMessage = "testMessage" + i;
            Assertions.assertEquals(expectedMessage, actualMessage);
        }
    }

    private void writeMockedRecords(int count, String message) {
        BlockingQueue<ConnectRecord> queue = connector.queue();
        for (int i = 0; i < count; i++) {
            RecordPartition partition = new RecordPartition();
            RecordOffset offset = new RecordOffset();
            ConnectRecord record = new ConnectRecord(partition, offset, System.currentTimeMillis(), message + i);
            queue.offer(record);
        }
    }

}
