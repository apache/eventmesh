package org.apache.eventmesh.api.connector.storage.data;

import org.apache.eventmesh.api.connector.storage.StorageConnector;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import lombok.Data;

@Data
public class PullRequest {

    private static final AtomicLong INCREASING_ID = new AtomicLong();

    private long id = INCREASING_ID.incrementAndGet();

    private String topicName;

    private String consumerGroupName;

    private StorageConnector storageConnector;

    private AtomicBoolean isEliminate = new AtomicBoolean(true);

    private AtomicInteger stock = new AtomicInteger();
}
