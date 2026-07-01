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
import org.apache.eventmesh.common.remote.JobState;
import org.apache.eventmesh.common.remote.request.FetchJobRequest;
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
import org.apache.eventmesh.runtime.service.health.HealthService;
import org.apache.eventmesh.runtime.service.monitor.MonitorService;
import org.apache.eventmesh.runtime.service.monitor.SinkMonitor;
import org.apache.eventmesh.runtime.service.monitor.SourceMonitor;
import org.apache.eventmesh.runtime.service.status.StatusService;
import org.apache.eventmesh.runtime.service.verify.VerifyService;
import org.apache.eventmesh.runtime.util.RuntimeUtils;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

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


    private final BlockingQueue<ConnectRecord> queue;

    private volatile boolean isRunning = false;

    private volatile boolean isFailed = false;

    public static final String CALLBACK_EXTENSION = "callBackExtension";

    private String adminServerAddr;

    private HealthService healthService;

    private MonitorService monitorService;

    private SourceMonitor sourceMonitor;

    private SinkMonitor sinkMonitor;

    private VerifyService verifyService;

    private StatusService statusService;


    public ConnectorRuntime(RuntimeInstanceConfig runtimeInstanceConfig) {
        this.runtimeInstanceConfig = runtimeInstanceConfig;
        this.queue = new LinkedBlockingQueue<>(1000);
    }

    @Override
    public void init() throws Exception {

        initAdminService();

        initStorageService();

        initStatusService();

        initConnectorService();

        initMonitorService();

        initHealthService();

        initVerfiyService();

    }

    private void initAdminService() {
        adminServerAddr = RuntimeUtils.getRandomAdminServerAddr(runtimeInstanceConfig.getAdminServiceAddr());
        // create gRPC channel
        channel = ManagedChannelBuilder.forTarget(adminServerAddr)
            .usePlaintext()
            .enableRetry()
            .maxRetryAttempts(3)
            .build();

        adminServiceStub = AdminServiceGrpc.newStub(channel).withWaitForReady();

        adminServiceBlockingStub = AdminServiceGrpc.newBlockingStub(channel).withWaitForReady();

    }

    private void initStorageService() {
        // TODO: init producer & consumer
        producer = StoragePluginFactory.getMeshMQProducer(runtimeInstanceConfig.getStoragePluginType());

        consumer = StoragePluginFactory.getMeshMQPushConsumer(runtimeInstanceConfig.getStoragePluginType());

    }

    private void initStatusService() {
        statusService = new StatusService(adminServiceStub, adminServiceBlockingStub);
    }

    private void initConnectorService() throws Exception {

        connectorRuntimeConfig = ConfigService.getInstance().buildConfigInstance(ConnectorRuntimeConfig.class);

        FetchJobResponse jobResponse = fetchJobConfig();
        log.info("fetch job config from admin server: {}", JsonUtils.toJSONString(jobResponse));

        if (jobResponse == null) {
            isFailed = true;
            stop();
            throw new RuntimeException("fetch job config fail");
        }

        connectorRuntimeConfig.setSourceConnectorType(jobResponse.getTransportType().getSrc().getName());
        connectorRuntimeConfig.setSourceConnectorDesc(jobResponse.getConnectorConfig().getSourceConnectorDesc());
        connectorRuntimeConfig.setSourceConnectorConfig(jobResponse.getConnectorConfig().getSourceConnectorConfig());

        connectorRuntimeConfig.setSinkConnectorType(jobResponse.getTransportType().getDst().getName());
        connectorRuntimeConfig.setSinkConnectorDesc(jobResponse.getConnectorConfig().getSinkConnectorDesc());
        connectorRuntimeConfig.setSinkConnectorConfig(jobResponse.getConnectorConfig().getSinkConnectorConfig());

        // spi load offsetMgmtService
        this.offsetManagement = new RecordOffsetManagement();
        this.committableOffsets = RecordOffsetManagement.CommittableOffsets.EMPTY;
        OffsetStorageConfig offsetStorageConfig = new OffsetStorageConfig();
        offsetStorageConfig.setOffsetStorageAddr(connectorRuntimeConfig.getRuntimeConfig().get("offsetStorageAddr").toString());
        offsetStorageConfig.setOffsetStorageType(connectorRuntimeConfig.getRuntimeConfig().get("offsetStoragePluginType").toString());
        offsetStorageConfig.setDataSourceType(jobResponse.getTransportType().getSrc());
        offsetStorageConfig.setDataSinkType(jobResponse.getTransportType().getDst());
        Map<String, String> offsetStorageExtensions = new HashMap<>();
        offsetStorageExtensions.put("jobId", connectorRuntimeConfig.getJobID());
        offsetStorageConfig.setExtensions(offsetStorageExtensions);

        this.offsetManagementService = Optional.ofNullable(offsetStorageConfig).map(OffsetStorageConfig::getOffsetStorageType)
            .map(storageType -> EventMeshExtensionFactory.getExtension(OffsetManagementService.class, storageType))
            .orElse(new DefaultOffsetManagementServiceImpl());
        this.offsetManagementService.initialize(offsetStorageConfig);
        this.offsetStorageWriter = new OffsetStorageWriterImpl(offsetManagementService);
        this.offsetStorageReader = new OffsetStorageReaderImpl(offsetManagementService);

        ConnectorCreateService<?> sourceConnectorCreateService =
            ConnectorPluginFactory.createConnector(connectorRuntimeConfig.getSourceConnectorType() + "-Source");
        sourceConnector = (Source) sourceConnectorCreateService.create();

        SourceConfig sourceConfig = (SourceConfig) ConfigUtil.parse(connectorRuntimeConfig.getSourceConnectorConfig(), sourceConnector.configClass());
        SourceConnectorContext sourceConnectorContext = new SourceConnectorContext();
        sourceConnectorContext.setSourceConfig(sourceConfig);
        sourceConnectorContext.setRuntimeConfig(connectorRuntimeConfig.getRuntimeConfig());
        sourceConnectorContext.setJobType(jobResponse.getType());
        sourceConnectorContext.setOffsetStorageReader(offsetStorageReader);
        if (CollectionUtils.isNotEmpty(jobResponse.getPosition())) {
            sourceConnectorContext.setRecordPositionList(jobResponse.getPosition());
        }
        sourceConnector.init(sourceConnectorContext);

        ConnectorCreateService<?> sinkConnectorCreateService =
            ConnectorPluginFactory.createConnector(connectorRuntimeConfig.getSinkConnectorType() + "-Sink");
        sinkConnector = (Sink) sinkConnectorCreateService.create();

        SinkConfig sinkConfig = (SinkConfig) ConfigUtil.parse(connectorRuntimeConfig.getSinkConnectorConfig(), sinkConnector.configClass());
        SinkConnectorContext sinkConnectorContext = new SinkConnectorContext();
        sinkConnectorContext.setSinkConfig(sinkConfig);
        sinkConnectorContext.setRuntimeConfig(connectorRuntimeConfig.getRuntimeConfig());
        sinkConnectorContext.setJobType(jobResponse.getType());
        sinkConnector.init(sinkConnectorContext);

        statusService.reportJobStatus(connectorRuntimeConfig.getJobID(), JobState.INIT);

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

    private void initMonitorService() {
        monitorService = new MonitorService(adminServiceStub, adminServiceBlockingStub);
        sourceMonitor = new SourceMonitor(connectorRuntimeConfig.getTaskID(), connectorRuntimeConfig.getJobID(), IPUtils.getLocalAddress());
        monitorService.registerMonitor(sourceMonitor);
        sinkMonitor = new SinkMonitor(connectorRuntimeConfig.getTaskID(), connectorRuntimeConfig.getJobID(), IPUtils.getLocalAddress());
        monitorService.registerMonitor(sinkMonitor);
    }

    private void initHealthService() {
        healthService = new HealthService(adminServiceStub, adminServiceBlockingStub, connectorRuntimeConfig);
    }

    private void initVerfiyService() {
        verifyService = new VerifyService(adminServiceStub, adminServiceBlockingStub, connectorRuntimeConfig);
    }

    @Override
    public void start() throws Exception {
        // start offsetMgmtService
        offsetManagementService.start();

        monitorService.start();

        healthService.start();

        isRunning = true;
        // start sinkService
        sinkService.execute(() -> {
            try {
                startSinkConnector();
            } catch (Exception e) {
                isFailed = true;
                log.error("sink connector start fail", e.getStackTrace());
                try {
                    this.stop();
                } catch (Exception ex) {
                    log.error("Failed to stop after exception", ex);
                }
            } finally {
                System.exit(-1);
            }
        });
        // start sourceService
        sourceService.execute(() -> {
            try {
                startSourceConnector();
            } catch (Exception e) {
                isFailed = true;
                log.error("source connector start fail", e);
                try {
                    this.stop();
                } catch (Exception ex) {
                    log.error("Failed to stop after exception", ex);
                }
            } finally {
                System.exit(-1);
            }
        });

        statusService.reportJobStatus(connectorRuntimeConfig.getJobID(), JobState.RUNNING);
    }

    @Override
    public void stop() throws Exception {
        log.info("ConnectorRuntime start stop");
        isRunning = false;
        if (isFailed) {
            statusService.reportJobStatus(connectorRuntimeConfig.getJobID(), JobState.FAIL);
        } else {
            statusService.reportJobStatus(connectorRuntimeConfig.getJobID(), JobState.COMPLETE);
        }
        sourceConnector.stop();
        sinkConnector.stop();
        monitorService.stop();
        healthService.stop();
        sourceService.shutdown();
        sinkService.shutdown();
        verifyService.stop();
        statusService.stop();
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
        log.info("ConnectorRuntime stopped");
    }

    private void startSourceConnector() throws Exception {
        sourceConnector.start();
        while (isRunning) {
            long sourceStartTime = System.currentTimeMillis();
            List<ConnectRecord> connectorRecordList = sourceConnector.poll();
            long sinkStartTime = System.currentTimeMillis();
            // TODO: use producer pub record to storage replace below
            if (connectorRecordList != null && !connectorRecordList.isEmpty()) {
                for (ConnectRecord record : connectorRecordList) {
                    // check recordUniqueId
                    if (record.getExtensions() == null || !record.getExtensions().containsKey("recordUniqueId")) {
                        record.addExtension("recordUniqueId", record.getRecordId());
                    }

                    // set a callback for this record
                    // if used the memory storage callback will be triggered after sink put success
                    record.setCallback(new SendMessageCallback() {
                        @Override
                        public void onSuccess(SendResult result) {
                            log.debug("send record to sink callback success, record: {}", record);
                            long sinkEndTime = System.currentTimeMillis();
                            sinkMonitor.recordProcess(sinkEndTime - sinkStartTime);
                            // commit record
                            sourceConnector.commit(record);
                            if (record.getPosition() != null) {
                                Optional<RecordOffsetManagement.SubmittedPosition> submittedRecordPosition = prepareToUpdateRecordOffset(record);
                                submittedRecordPosition.ifPresent(RecordOffsetManagement.SubmittedPosition::ack);
                                log.debug("start wait all messages to commit");
                                offsetManagement.awaitAllMessages(5000, TimeUnit.MILLISECONDS);
                                // update & commit offset
                                updateCommittableOffsets();
                                commitOffsets();
                            }
                            Optional<SendMessageCallback> callback =
                                Optional.ofNullable(record.getExtensionObj(CALLBACK_EXTENSION)).map(v -> (SendMessageCallback) v);
                            callback.ifPresent(cb -> cb.onSuccess(convertToSendResult(record)));
                        }

                        @Override
                        public void onException(SendExceptionContext sendExceptionContext) {
                            isFailed = true;
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
                    long sourceEndTime = System.currentTimeMillis();
                    sourceMonitor.recordProcess(sourceEndTime - sourceStartTime);

                    // if enabled incremental data reporting consistency check
                    if (connectorRuntimeConfig.enableIncrementalDataConsistencyCheck) {
                        verifyService.reportVerifyRequest(record, ConnectorStage.SOURCE);
                    }

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
                verifyService.reportVerifyRequest(connectRecord, ConnectorStage.SINK);
            }
        }
    }
}
