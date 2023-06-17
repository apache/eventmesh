/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.source.connector.s3.connector;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.eventmesh.openconnect.api.data.ConnectRecord;
import org.apache.eventmesh.source.connector.s3.config.ConnectorConfig;
import org.apache.eventmesh.source.connector.s3.config.S3SourceConfig;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Ignore
public class S3SourceConnectorTest {

    private static final S3SourceConfig sourceConfig;

    private static final ConnectorConfig connectorConfig;

    private static final Map<String, Integer> schema;

    private static final int eachRecordSize;

    static {
        sourceConfig = new S3SourceConfig();
        connectorConfig = new ConnectorConfig();
        connectorConfig.setConnectorName("S3SourceConnector");
        connectorConfig.setRegion("ap-southeast-1");
        connectorConfig.setBucket("event-mesh-bucket");
        connectorConfig.setAccessKey("access-key");
        connectorConfig.setSecretKey("secret-key");

        connectorConfig.setFileName("test-file");

        schema = new HashMap<>();
        schema.put("id", 4);
        schema.put("body", 16);
        schema.put("time", 8);

        eachRecordSize = schema.values().stream().reduce((x, y) -> x + y).orElse(0);
        connectorConfig.setSchema(schema);
        sourceConfig.setConnectorConfig(connectorConfig);
    }

    private S3AsyncClient s3Client;

    @Before
    public void setUp() throws Exception {
        AwsBasicCredentials basicCredentials = AwsBasicCredentials.create(this.connectorConfig.getAccessKey(),
            this.connectorConfig.getSecretKey());
        this.s3Client = S3AsyncClient.builder().credentialsProvider(() -> basicCredentials)
            .region(Region.of(this.connectorConfig.getRegion())).build();

        // write mocked data
        this.writeMockedRecords(200);
    }

    @After
    public void tearDown() throws Exception {
        // clear file
        this.s3Client.deleteObject(builder -> builder.bucket(this.connectorConfig.getBucket())
            .key(this.connectorConfig.getFileName()).build()).get();
    }

    @Test
    public void testS3SourceConnector() throws Exception {
        S3SourceConnector s3SourceConnector = new S3SourceConnector();
        s3SourceConnector.init(sourceConfig);
        s3SourceConnector.start();
        int expectedId = 0;
        while (true) {
            List<ConnectRecord> connectRecords = s3SourceConnector.poll();
            if (connectRecords.isEmpty()) {
                break;
            }
            Assert.assertEquals(20, connectRecords.size());
            for (ConnectRecord connectRecord : connectRecords) {
                byte[] data = (byte[]) connectRecord.getData();
                Assert.assertEquals(eachRecordSize, data.length);
                ByteBuffer byteBuffer = ByteBuffer.wrap(data);
                int id = byteBuffer.getInt();
                Assert.assertEquals(expectedId++, id);
            }
        }

    }

    private void writeMockedRecords(int count) throws Exception {
        ByteBuffer bytes = ByteBuffer.allocate(count * eachRecordSize);
        ByteBuffer body = ByteBuffer.allocate(16);
        body.putLong(13L);
        body.putLong(13L);
        body.flip();
        for (int i = 0; i < count; i++) {
            bytes.putInt(i);
            bytes.put(body);
            body.flip();
            bytes.putLong(System.currentTimeMillis());
        }
        PutObjectRequest putObjectRequest = PutObjectRequest.builder().bucket(connectorConfig.getBucket()).key(connectorConfig.getFileName()).build();
        AsyncRequestBody requestBody = AsyncRequestBody.fromBytes(bytes.array());
        this.s3Client.putObject(putObjectRequest, requestBody).get();
    }

}
