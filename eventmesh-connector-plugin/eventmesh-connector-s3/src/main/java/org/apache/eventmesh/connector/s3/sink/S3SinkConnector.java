package org.apache.eventmesh.connector.s3.sink;

import org.apache.eventmesh.connector.SinkConnector;

import java.nio.charset.StandardCharsets;


import java.util.List;
import java.util.Properties;
import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
@Slf4j
public class S3SinkConnector implements SinkConnector {
    private S3Client s3;
    private String bucket;
    private Properties props;
    @Override
    public void init(Properties props) {
        this.props = props;
        bucket = props.getProperty("connector.bucket", "sink");
        s3 = S3Client.create();
    }
    @Override
    public void put(List<CloudEvent> events) {
        for (CloudEvent event : events) {
            byte[] data = event.getData() != null ? event.getData().toBytes() : new byte[0];
            String key = event.getId() != null ? event.getId() : String.valueOf(System.nanoTime());
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(),
                software.amazon.awssdk.core.sync.RequestBody.fromBytes(data));
        }
    }
    @Override
    public void commit(List<CloudEvent> written) {

    }
}
