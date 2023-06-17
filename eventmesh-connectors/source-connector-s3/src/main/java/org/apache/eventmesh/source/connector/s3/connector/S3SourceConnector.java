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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.eventmesh.openconnect.api.config.Config;
import org.apache.eventmesh.openconnect.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.api.data.RecordOffset;
import org.apache.eventmesh.openconnect.api.data.RecordPartition;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.source.connector.s3.config.ConnectorConfig;
import org.apache.eventmesh.source.connector.s3.config.S3SourceConfig;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class S3SourceConnector implements Source {

    public static final String REGION = "region";

    public static final String BUCKET = "bucket";

    public static final String FILE_NAME = "fileName";

    public static final String POSITION = "position";

    private S3SourceConfig sourceConfig;

    private ConnectorConfig connectorConfig;

    private int eachRecordSize;

    private long fileSize;

    private S3AsyncClient s3Client;

    private long position;


    @Override
    public Class<? extends Config> configClass() {
        return S3SourceConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
        // init config for s3 source connector
        this.sourceConfig = (S3SourceConfig) config;
        this.connectorConfig = this.sourceConfig.getConnectorConfig();
        this.eachRecordSize = calculateEachRecordSize();
        AwsBasicCredentials basicCredentials = AwsBasicCredentials.create(this.connectorConfig.getAccessKey(),
            this.connectorConfig.getSecretKey());
        this.s3Client = S3AsyncClient.builder().credentialsProvider(() -> basicCredentials)
            .region(Region.of(this.connectorConfig.getRegion())).build();
    }

    private int calculateEachRecordSize() {
        Optional<Integer> sum = this.connectorConfig.getSchema().values().stream().reduce((x, y) -> x + y);
        return sum.orElse(0);
    }

    @Override
    public void start() throws Exception {
        CompletableFuture<HeadObjectResponse> headObjectResponseCompletableFuture = this.s3Client.headObject(
            builder -> builder.bucket(this.connectorConfig.getBucket()).key(this.connectorConfig.getFileName()));
        headObjectResponseCompletableFuture.get(this.connectorConfig.getTimeout(), TimeUnit.MILLISECONDS);
        this.fileSize = headObjectResponseCompletableFuture.get().contentLength();
    }

    @Override
    public void commit(ConnectRecord record) {

    }

    @Override
    public String name() {
        return this.sourceConfig.getConnectorConfig().getConnectorName();
    }

    @Override
    public void stop() throws Exception {

    }

    @Override
    public List<ConnectRecord> poll() {
        if (this.position >= this.fileSize) {
            return Collections.EMPTY_LIST;
        }
        long startPosition = this.position;
        long endPosition = Math.max(this.fileSize, this.position + this.eachRecordSize * this.connectorConfig.getBatchSize());
        GetObjectRequest request = GetObjectRequest.builder().bucket(this.connectorConfig.getBucket()).key(this.connectorConfig.getFileName())
            .range("bytes=" + startPosition + "-" + endPosition).build();
        ResponseBytes<GetObjectResponse> resp;
        try {
            resp = this.s3Client.getObject(request, AsyncResponseTransformer.toBytes())
                .get(this.connectorConfig.getTimeout(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("poll records from S3 file, poll range {}-{}, but failed", startPosition, endPosition, e);
            return Collections.EMPTY_LIST;
        }
        byte[] bytes = resp.asByteArray();
        List<ConnectRecord> records = new ArrayList<>(bytes.length / this.eachRecordSize);
        for (int i = 0; i < bytes.length; i += this.eachRecordSize) {
            byte[] body = new byte[this.eachRecordSize];
            System.arraycopy(bytes, i, body, 0, this.eachRecordSize);
            this.position += this.eachRecordSize;
            ConnectRecord record = new ConnectRecord(getRecordPartition(), getRecordOffset(), System.currentTimeMillis(), body);
            records.add(record);
        }
        return records;
    }

    private RecordPartition getRecordPartition() {
        Map<String, String> map = new HashMap<>();
        map.put(REGION, this.connectorConfig.getRegion());
        map.put(BUCKET, this.connectorConfig.getBucket());
        map.put(FILE_NAME, this.connectorConfig.getFileName());
        return new RecordPartition(map);
    }

    private RecordOffset getRecordOffset() {
        Map<String, String> map = new HashMap<>();
        map.put(POSITION, String.valueOf(this.position));
        return new RecordOffset(map);
    }
}
