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

package org.apache.eventmesh.connector.s3.source.connector;

import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.config.connector.s3.S3SourceConfig;
import org.apache.eventmesh.common.config.connector.s3.SourceConnectorConfig;
import org.apache.eventmesh.common.remote.offset.RecordOffset;
import org.apache.eventmesh.common.remote.offset.RecordPartition;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SourceConnectorContext;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.common.remote.offset.S3.S3RecordOffset;
import org.apache.eventmesh.common.remote.offset.S3.S3RecordPartition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

@Slf4j
public class S3SourceConnector implements Source {

    public static final String REGION = "region";

    public static final String BUCKET = "bucket";

    public static final String FILE_NAME = "fileName";

    public static final String POSITION = "position";

    private S3SourceConfig sourceConfig;

    private SourceConnectorConfig sourceConnectorConfig;

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
        doInit();
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        SourceConnectorContext sourceConnectorContext = (SourceConnectorContext) connectorContext;
        this.sourceConfig = (S3SourceConfig) sourceConnectorContext.getSourceConfig();
        doInit();
    }

    private void doInit() {
        this.sourceConnectorConfig = this.sourceConfig.getSourceConnectorConfig();
        this.eachRecordSize = calculateEachRecordSize();
        AwsBasicCredentials basicCredentials = AwsBasicCredentials.create(this.sourceConnectorConfig.getAccessKey(),
            this.sourceConnectorConfig.getSecretKey());
        this.s3Client = S3AsyncClient.builder().credentialsProvider(() -> basicCredentials)
            .region(Region.of(this.sourceConnectorConfig.getRegion())).build();
    }

    private int calculateEachRecordSize() {
        Optional<Integer> sum = this.sourceConnectorConfig.getSchema().values().stream().reduce((x, y) -> x + y);
        return sum.orElse(0);
    }

    @Override
    public void start() throws Exception {
        CompletableFuture<HeadObjectResponse> headObjectResponseCompletableFuture = this.s3Client.headObject(
            builder -> builder.bucket(this.sourceConnectorConfig.getBucket()).key(this.sourceConnectorConfig.getFileName()));
        headObjectResponseCompletableFuture.get(this.sourceConnectorConfig.getTimeout(), TimeUnit.MILLISECONDS);
        this.fileSize = headObjectResponseCompletableFuture.get().contentLength();
    }

    @Override
    public void commit(ConnectRecord record) {

    }

    @Override
    public String name() {
        return this.sourceConfig.getSourceConnectorConfig().getConnectorName();
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
        long endPosition = Math.min(this.fileSize, this.position + this.eachRecordSize * this.sourceConnectorConfig.getBatchSize()) - 1;
        GetObjectRequest request = GetObjectRequest.builder().bucket(this.sourceConnectorConfig.getBucket())
            .key(this.sourceConnectorConfig.getFileName())
            .range("bytes=" + startPosition + "-" + endPosition).build();
        ResponseBytes<GetObjectResponse> resp;
        try {
            resp = this.s3Client.getObject(request, AsyncResponseTransformer.toBytes())
                .get(this.sourceConnectorConfig.getTimeout(), TimeUnit.MILLISECONDS);
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
        S3RecordPartition s3RecordPartition = new S3RecordPartition();
        s3RecordPartition.setRegion(this.sourceConnectorConfig.getRegion());
        s3RecordPartition.setBucket(this.sourceConnectorConfig.getBucket());
        s3RecordPartition.setFileName(this.sourceConnectorConfig.getFileName());
        s3RecordPartition.setClazz(s3RecordPartition.getRecordPartitionClass());
        return s3RecordPartition;
    }

    private RecordOffset getRecordOffset() {
        S3RecordOffset s3RecordOffset = new S3RecordOffset();
        s3RecordOffset.setOffset(this.position);
        s3RecordOffset.setClazz(s3RecordOffset.getRecordOffsetClass());
        return s3RecordOffset;
    }
}
