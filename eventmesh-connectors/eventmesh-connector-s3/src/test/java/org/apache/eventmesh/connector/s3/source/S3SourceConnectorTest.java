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

package org.apache.eventmesh.connector.s3.source;

import org.apache.eventmesh.connector.s3.source.config.S3SourceConfig;
import org.apache.eventmesh.connector.s3.source.config.SourceConnectorConfig;
import org.apache.eventmesh.connector.s3.source.connector.S3SourceConnector;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private static final SourceConnectorConfig SOURCE_CONNECTOR_CONFIG;

    private static final Map<String, Integer> schema;

    private static final int eachRecordSize;

    static {
        sourceConfig = new S3SourceConfig();
        SOURCE_CONNECTOR_CONFIG = new SourceConnectorConfig();
        SOURCE_CONNECTOR_CONFIG.setConnectorName("S3SourceConnector");
        SOURCE_CONNECTOR_CONFIG.setRegion("ap-southeast-1");
        SOURCE_CONNECTOR_CONFIG.setBucket("event-mesh-bucket");
        SOURCE_CONNECTOR_CONFIG.setAccessKey("access-key");
        SOURCE_CONNECTOR_CONFIG.setSecretKey("secret-key");

        SOURCE_CONNECTOR_CONFIG.setFileName("test-file");

        schema = new HashMap<>();
        schema.put("id", 4);
        schema.put("body", 16);
        schema.put("time", 8);

        eachRecordSize = schema.values().stream().reduce((x, y) -> x + y).orElse(0);
        SOURCE_CONNECTOR_CONFIG.setSchema(schema);
        sourceConfig.setSourceConnectorConfig(SOURCE_CONNECTOR_CONFIG);
    }

    private S3AsyncClient s3Client;

    @Before
    public void setUp() throws Exception {
        AwsBasicCredentials basicCredentials = AwsBasicCredentials.create(this.SOURCE_CONNECTOR_CONFIG.getAccessKey(),
                this.SOURCE_CONNECTOR_CONFIG.getSecretKey());
        this.s3Client = S3AsyncClient.builder().credentialsProvider(() -> basicCredentials)
                .region(Region.of(this.SOURCE_CONNECTOR_CONFIG.getRegion())).build();

        // write mocked data
        this.writeMockedRecords(200);
    }

    @After
    public void tearDown() throws Exception {
        // clear file
        this.s3Client.deleteObject(builder -> builder.bucket(this.SOURCE_CONNECTOR_CONFIG.getBucket())
                .key(this.SOURCE_CONNECTOR_CONFIG.getFileName()).build()).get();
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
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(SOURCE_CONNECTOR_CONFIG.getBucket()).key(SOURCE_CONNECTOR_CONFIG.getFileName()).build();
        AsyncRequestBody requestBody = AsyncRequestBody.fromBytes(bytes.array());
        this.s3Client.putObject(putObjectRequest, requestBody).get();
    }

}
