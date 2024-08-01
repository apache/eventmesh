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

package org.apache.eventmesh.runtime.connector;

import org.apache.eventmesh.api.consumer.Consumer;
import org.apache.eventmesh.api.factory.StoragePluginFactory;
import org.apache.eventmesh.api.producer.Producer;
import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.common.config.ConfigService;
import org.apache.eventmesh.common.config.connector.SinkConfig;
import org.apache.eventmesh.common.config.connector.SourceConfig;
import org.apache.eventmesh.common.config.connector.offset.OffsetStorageConfig;
import org.apache.eventmesh.common.enums.ConnectorStage;
import org.apache.eventmesh.common.protocol.grpc.adminserver.AdminServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.adminserver.AdminServiceGrpc.AdminServiceBlockingStub;
import org.apache.eventmesh.common.protocol.grpc.adminserver.AdminServiceGrpc.AdminServiceStub;
import org.apache.eventmesh.common.protocol.grpc.adminserver.Metadata;
import org.apache.eventmesh.common.protocol.grpc.adminserver.Payload;
import org.apache.eventmesh.common.remote.request.FetchJobRequest;
import org.apache.eventmesh.common.remote.request.ReportHeartBeatRequest;
import org.apache.eventmesh.common.remote.request.ReportVerifyRequest;
import org.apache.eventmesh.common.remote.response.FetchJobResponse;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.openconnect.api.ConnectorCreateService;
import org.apache.eventmesh.openconnect.api.connector.SinkConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SourceConnectorContext;
import org.apache.eventmesh.openconnect.api.factory.ConnectorPluginFactory;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.openconnect.offsetmgmt.api.callback.SendExceptionContext;
import org.apache.eventmesh.openconnect.offsetmgmt.api.callback.SendMessageCallback;
import org.apache.eventmesh.openconnect.offsetmgmt.api.callback.SendResult;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.RecordOffsetManagement;
import org.apache.eventmesh.openconnect.offsetmgmt.api.storage.DefaultOffsetManagementServiceImpl;
import org.apache.eventmesh.openconnect.offsetmgmt.api.storage.OffsetManagementService;
import org.apache.eventmesh.openconnect.offsetmgmt.api.storage.OffsetStorageReaderImpl;
import org.apache.eventmesh.openconnect.offsetmgmt.api.storage.OffsetStorageWriterImpl;
import org.apache.eventmesh.openconnect.util.ConfigUtil;
import org.apache.eventmesh.runtime.Runtime;
import org.apache.eventmesh.runtime.RuntimeInstanceConfig;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import com.google.protobuf.Any;
import com.google.protobuf.UnsafeByteOperations;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConnectorRuntime implements Runtime {

    private RuntimeInstanceConfig runtimeInstanceConfig;

    private ConnectorRuntimeConfig connectorRuntimeConfig;

    private ManagedChannel channel;

    private AdminServiceStub adminServiceStub;

    private AdminServiceBlockingStub adminServiceBlockingStub;

    StreamObserver<Payload> responseObserver;

    StreamObserver<Payload> requestObserver;

    private Source sourceConnector;

    private Sink sinkConnector;

    private OffsetStorageWriterImpl offsetStorageWriter;

    private OffsetStorageReaderImpl offsetStorageReader;

    private OffsetManagementService offsetManagementService;

    private RecordOffsetManagement offsetManagement;

    private volatile RecordOffsetManagement.CommittableOffsets committableOffsets;

    private Producer producer;

    private Consumer consumer;

    private final ExecutorService sourceService = ThreadPoolFactory.createSingleExecutor("eventMesh-sourceService");

    private final ExecutorService sinkService = ThreadPoolFactory.createSingleExecutor("eventMesh-sinkService");

    private final ScheduledExecutorService heartBeatExecutor = Executors.newSingleThreadScheduledExecutor();

    private final BlockingQueue<ConnectRecord> queue;

    private volatile boolean isRunning = false;

    public static final String CALLBACK_EXTENSION = "callBackExtension";


    public ConnectorRuntime(RuntimeInstanceConfig runtimeInstanceConfig) {
        this.runtimeInstanceConfig = runtimeInstanceConfig;
        this.queue = new LinkedBlockingQueue<>(1000);
    }

    @Override
    public void init() throws Exception {

        initAdminService();

        initStorageService();

        initConnectorService();
    }

    private void initAdminService() {
        // create gRPC channel
        channel = ManagedChannelBuilder.forTarget(runtimeInstanceConfig.getAdminServerAddr()).usePlaintext().build();

        adminServiceStub = AdminServiceGrpc.newStub(channel).withWaitForReady();

        adminServiceBlockingStub = AdminServiceGrpc.newBlockingStub(channel).withWaitForReady();

        responseObserver = new StreamObserver<Payload>() {
            @Override
            public void onNext(Payload response) {
                log.info("runtime receive message: {} ", response);
            }

            @Override
            public void onError(Throwable t) {
                log.error("runtime receive error message: {}", t.getMessage());
            }

            @Override
            public void onCompleted() {
                log.info("runtime finished receive message and completed");
            }
        };

        requestObserver = adminServiceStub.invokeBiStream(responseObserver);
    }

    private void initStorageService() {
        // TODO: init producer & consumer
        producer = StoragePluginFactory.getMeshMQProducer(runtimeInstanceConfig.getStoragePluginType());

        consumer = StoragePluginFactory.getMeshMQPushConsumer(runtimeInstanceConfig.getStoragePluginType());

    }

    private void initConnectorService() throws Exception {

        connectorRuntimeConfig = ConfigService.getInstance().buildConfigInstance(ConnectorRuntimeConfig.class);

        FetchJobResponse jobResponse = fetchJobConfig();

        if (jobResponse == null) {
            throw new RuntimeException("fetch job config fail");
        }

        connectorRuntimeConfig.setSourceConnectorType(jobResponse.getTransportType().getSrc().getName());
        connectorRuntimeConfig.setSourceConnectorDesc(jobResponse.getConnectorConfig().getSourceConnectorDesc());
        connectorRuntimeConfig.setSourceConnectorConfig(jobResponse.getConnectorConfig().getSourceConnectorConfig());

        connectorRuntimeConfig.setSinkConnectorType(jobResponse.getTransportType().getDst().getName());
        connectorRuntimeConfig.setSinkConnectorDesc(jobResponse.getConnectorConfig().getSinkConnectorDesc());
        connectorRuntimeConfig.setSinkConnectorConfig(jobResponse.getConnectorConfig().getSinkConnectorConfig());

        ConnectorCreateService<?> sourceConnectorCreateService =
            ConnectorPluginFactory.createConnector(connectorRuntimeConfig.getSourceConnectorType() + "-Source");
        sourceConnector = (Source) sourceConnectorCreateService.create();

        SourceConfig sourceConfig = (SourceConfig) ConfigUtil.parse(connectorRuntimeConfig.getSourceConnectorConfig(), sourceConnector.configClass());
        SourceConnectorContext sourceConnectorContext = new SourceConnectorContext();
        sourceConnectorContext.setSourceConfig(sourceConfig);
        sourceConnectorContext.setRuntimeConfig(connectorRuntimeConfig.getRuntimeConfig());
        sourceConnectorContext.setOffsetStorageReader(offsetStorageReader);
        if (CollectionUtils.isNotEmpty(jobResponse.getPosition())) {
            sourceConnectorContext.setRecordPositionList(jobResponse.getPosition());
        }

        // spi load offsetMgmtService
        this.offsetManagement = new RecordOffsetManagement();
        this.committableOffsets = RecordOffsetManagement.CommittableOffsets.EMPTY;
        OffsetStorageConfig offsetStorageConfig = sourceConfig.getOffsetStorageConfig();
        offsetStorageConfig.setDataSourceType(jobResponse.getTransportType().getSrc());
        offsetStorageConfig.setDataSinkType(jobResponse.getTransportType().getDst());
        this.offsetManagementService = Optional.ofNullable(offsetStorageConfig).map(OffsetStorageConfig::getOffsetStorageType)
            .map(storageType -> EventMeshExtensionFactory.getExtension(OffsetManagementService.class, storageType))
            .orElse(new DefaultOffsetManagementServiceImpl());
        this.offsetManagementService.initialize(offsetStorageConfig);
        this.offsetStorageWriter = new OffsetStorageWriterImpl(offsetManagementService);
        this.offsetStorageReader = new OffsetStorageReaderImpl(offsetManagementService);

        sourceConnector.init(sourceConnectorContext);

        ConnectorCreateService<?> sinkConnectorCreateService =
            ConnectorPluginFactory.createConnector(connectorRuntimeConfig.getSinkConnectorType() + "-Sink");
        sinkConnector = (Sink) sinkConnectorCreateService.create();

        SinkConfig sinkConfig = (SinkConfig) ConfigUtil.parse(connectorRuntimeConfig.getSinkConnectorConfig(), sinkConnector.configClass());
        SinkConnectorContext sinkConnectorContext = new SinkConnectorContext();
        sinkConnectorContext.setSinkConfig(sinkConfig);
        sinkConnector.init(sinkConnectorContext);

    }

    private FetchJobResponse fetchJobConfig() {
        String jobId = connectorRuntimeConfig.getJobID();
        FetchJobRequest jobRequest = new FetchJobRequest();
        jobRequest.setJobID(jobId);

        Metadata metadata = Metadata.newBuilder().setType(FetchJobRequest.class.getSimpleName()).build();

        Payload request = Payload.newBuilder().setMetadata(metadata)
            .setBody(Any.newBuilder().setValue(UnsafeByteOperations.unsafeWrap(Objects.requireNonNull(JsonUtils.toJSONBytes(jobRequest)))).build())
            .build();
        Payload response = adminServiceBlockingStub.invoke(request);
        if (response.getMetadata().getType().equals(FetchJobResponse.class.getSimpleName())) {
            return JsonUtils.parseObject(response.getBody().getValue().toStringUtf8(), FetchJobResponse.class);
        }
        return null;
    }

    @Override
    public void start() throws Exception {

        heartBeatExecutor.scheduleAtFixedRate(() -> {

            ReportHeartBeatRequest heartBeat = new ReportHeartBeatRequest();
            heartBeat.setAddress(IPUtils.getLocalAddress());
            heartBeat.setReportedTimeStamp(String.valueOf(System.currentTimeMillis()));
            heartBeat.setJobID(connectorRuntimeConfig.getJobID());

            Metadata metadata = Metadata.newBuilder().setType(ReportHeartBeatRequest.class.getSimpleName()).build();

            Payload request = Payload.newBuilder().setMetadata(metadata)
                .setBody(Any.newBuilder().setValue(UnsafeByteOperations.unsafeWrap(Objects.requireNonNull(JsonUtils.toJSONBytes(heartBeat)))).build())
                .build();

            requestObserver.onNext(request);
        }, 5, 5, TimeUnit.SECONDS);

        // start offsetMgmtService
        offsetManagementService.start();
        isRunning = true;
        // start sinkService
        sinkService.execute(() -> {
            try {
                startSinkConnector();
            } catch (Exception e) {
                log.error("sink connector [{}] start fail", sinkConnector.name(), e);
                try {
                    this.stop();
                } catch (Exception ex) {
                    log.error("Failed to stop after exception", ex);
                }
                throw new RuntimeException(e);
            }
        });
        // start
        sourceService.execute(() -> {
            try {
                startSourceConnector();
            } catch (Exception e) {
                log.error("source connector [{}] start fail", sourceConnector.name(), e);
                try {
                    this.stop();
                } catch (Exception ex) {
                    log.error("Failed to stop after exception", ex);
                }
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void stop() throws Exception {
        sourceConnector.stop();
        sinkConnector.stop();
        sourceService.shutdown();
        sinkService.shutdown();
        heartBeatExecutor.shutdown();
        requestObserver.onCompleted();
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
        }
    }

    private void startSourceConnector() throws Exception {
        sourceConnector.start();
        while (isRunning) {
            List<ConnectRecord> connectorRecordList = sourceConnector.poll();
            // TODO: use producer pub record to storage replace below
            if (connectorRecordList != null && !connectorRecordList.isEmpty()) {
                for (ConnectRecord record : connectorRecordList) {
                    // if enabled incremental data reporting consistency check
                    if (connectorRuntimeConfig.enableIncrementalDataConsistencyCheck) {
                        reportVerifyRequest(record, connectorRuntimeConfig, ConnectorStage.SOURCE);
                    }

                    // set a callback for this record
                    // if used the memory storage callback will be triggered after sink put success
                    record.setCallback(new SendMessageCallback() {
                        @Override
                        public void onSuccess(SendResult result) {
                            // commit record
                            sourceConnector.commit(record);
                            Optional<RecordOffsetManagement.SubmittedPosition> submittedRecordPosition = prepareToUpdateRecordOffset(record);
                            submittedRecordPosition.ifPresent(RecordOffsetManagement.SubmittedPosition::ack);
                            Optional<SendMessageCallback> callback =
                                Optional.ofNullable(record.getExtensionObj(CALLBACK_EXTENSION)).map(v -> (SendMessageCallback) v);
                            callback.ifPresent(cb -> cb.onSuccess(convertToSendResult(record)));
                        }

                        @Override
                        public void onException(SendExceptionContext sendExceptionContext) {
                            // handle exception
                            sourceConnector.onException(record);
                            log.error("send record to sink callback exception, process will shut down, record: {}", record,
                                sendExceptionContext.getCause());
                            try {
                                stop();
                            } catch (Exception e) {
                                log.error("Failed to stop after exception", e);
                            }
                        }
                    });

                    queue.put(record);

                    offsetManagement.awaitAllMessages(5000, TimeUnit.MILLISECONDS);
                    // update & commit offset
                    updateCommittableOffsets();
                    commitOffsets();
                }
            }
        }
    }

    private SendResult convertToSendResult(ConnectRecord record) {
        SendResult result = new SendResult();
        result.setMessageId(record.getRecordId());
        if (StringUtils.isNotEmpty(record.getExtension("topic"))) {
            result.setTopic(record.getExtension("topic"));
        }
        return result;
    }

    private void reportVerifyRequest(ConnectRecord record, ConnectorRuntimeConfig connectorRuntimeConfig, ConnectorStage connectorStage) {
        String md5Str = md5(record.toString());
        ReportVerifyRequest reportVerifyRequest = new ReportVerifyRequest();
        reportVerifyRequest.setTaskID(connectorRuntimeConfig.getTaskID());
        reportVerifyRequest.setRecordID(record.getRecordId());
        reportVerifyRequest.setRecordSig(md5Str);
        reportVerifyRequest.setConnectorName(
            IPUtils.getLocalAddress() + "_" + connectorRuntimeConfig.getJobID() + "_" + connectorRuntimeConfig.getRegion());
        reportVerifyRequest.setConnectorStage(connectorStage.name());
        reportVerifyRequest.setPosition(JsonUtils.toJSONString(record.getPosition()));

        Metadata metadata = Metadata.newBuilder().setType(ReportVerifyRequest.class.getSimpleName()).build();

        Payload request = Payload.newBuilder().setMetadata(metadata)
            .setBody(Any.newBuilder().setValue(UnsafeByteOperations.unsafeWrap(Objects.requireNonNull(JsonUtils.toJSONBytes(reportVerifyRequest))))
                .build())
            .build();

        requestObserver.onNext(request);
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : messageDigest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<RecordOffsetManagement.SubmittedPosition> prepareToUpdateRecordOffset(ConnectRecord record) {
        return Optional.of(this.offsetManagement.submitRecord(record.getPosition()));
    }

    public void updateCommittableOffsets() {
        RecordOffsetManagement.CommittableOffsets newOffsets = offsetManagement.committableOffsets();
        synchronized (this) {
            this.committableOffsets = this.committableOffsets.updatedWith(newOffsets);
        }
    }

    public boolean commitOffsets() {
        log.info("Start Committing offsets");

        long timeout = System.currentTimeMillis() + 5000L;

        RecordOffsetManagement.CommittableOffsets offsetsToCommit;
        synchronized (this) {
            offsetsToCommit = this.committableOffsets;
            this.committableOffsets = RecordOffsetManagement.CommittableOffsets.EMPTY;
        }

        if (committableOffsets.isEmpty()) {
            log.debug(
                "Either no records were produced since the last offset commit, "
                    + "or every record has been filtered out by a transformation or dropped due to transformation or conversion errors.");
            // We continue with the offset commit process here instead of simply returning immediately
            // in order to invoke SourceTask::commit and record metrics for a successful offset commit
        } else {
            log.info("{} Committing offsets for {} acknowledged messages", this, committableOffsets.numCommittableMessages());
            if (committableOffsets.hasPending()) {
                log.debug(
                    "{} There are currently {} pending messages spread across {} source partitions whose offsets will not be committed."
                        + " The source partition with the most pending messages is {}, with {} pending messages",
                    this,
                    committableOffsets.numUncommittableMessages(), committableOffsets.numDeques(), committableOffsets.largestDequePartition(),
                    committableOffsets.largestDequeSize());
            } else {
                log.debug(
                    "{} There are currently no pending messages for this offset commit; "
                        + "all messages dispatched to the task's producer since the last commit have been acknowledged",
                    this);
            }
        }

        // write offset to memory
        offsetsToCommit.offsets().forEach(offsetStorageWriter::writeOffset);

        // begin flush
        if (!offsetStorageWriter.beginFlush()) {
            return true;
        }

        // using offsetManagementService to persist offset
        Future<Void> flushFuture = offsetStorageWriter.doFlush();
        try {
            flushFuture.get(Math.max(timeout - System.currentTimeMillis(), 0), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            log.warn("{} Flush of offsets interrupted, cancelling", this);
            offsetStorageWriter.cancelFlush();
            return false;
        } catch (ExecutionException e) {
            log.error("{} Flush of offsets threw an unexpected exception: ", this, e);
            offsetStorageWriter.cancelFlush();
            return false;
        } catch (TimeoutException e) {
            log.error("{} Timed out waiting to flush offsets to storage; will try again on next flush interval with latest offsets", this);
            offsetStorageWriter.cancelFlush();
            return false;
        }
        return true;
    }

    private void startSinkConnector() throws Exception {
        sinkConnector.start();
        while (isRunning) {
            // TODO: use consumer sub from storage to replace below
            ConnectRecord connectRecord = null;
            try {
                connectRecord = queue.poll(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("poll connect record error", e);
            }
            if (connectRecord == null) {
                continue;
            }
            List<ConnectRecord> connectRecordList = new ArrayList<>();
            connectRecordList.add(connectRecord);
            sinkConnector.put(connectRecordList);
            // if enabled incremental data reporting consistency check
            if (connectorRuntimeConfig.enableIncrementalDataConsistencyCheck) {
                reportVerifyRequest(connectRecord, connectorRuntimeConfig, ConnectorStage.SINK);
            }
        }
    }
}
