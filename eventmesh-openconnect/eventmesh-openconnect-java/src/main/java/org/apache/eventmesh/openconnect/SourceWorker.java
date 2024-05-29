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

package org.apache.eventmesh.openconnect;

import static org.apache.eventmesh.common.Constants.CLOUD_EVENTS_PROTOCOL_NAME;

import org.apache.eventmesh.client.tcp.EventMeshTCPClient;
import org.apache.eventmesh.client.tcp.EventMeshTCPClientFactory;
import org.apache.eventmesh.client.tcp.common.MessageUtils;
import org.apache.eventmesh.client.tcp.conf.EventMeshTCPClientConfig;
import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.common.config.connector.SourceConfig;
import org.apache.eventmesh.common.config.connector.offset.OffsetStorageConfig;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.protocol.tcp.OPStatus;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.common.utils.SystemUtils;
import org.apache.eventmesh.openconnect.api.callback.SendExcepionContext;
import org.apache.eventmesh.openconnect.api.callback.SendMessageCallback;
import org.apache.eventmesh.openconnect.api.callback.SendResult;
import org.apache.eventmesh.openconnect.api.connector.SourceConnectorContext;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.RecordOffsetManagement;
import org.apache.eventmesh.openconnect.offsetmgmt.api.storage.DefaultOffsetManagementServiceImpl;
import org.apache.eventmesh.openconnect.offsetmgmt.api.storage.OffsetManagementService;
import org.apache.eventmesh.openconnect.offsetmgmt.api.storage.OffsetStorageReaderImpl;
import org.apache.eventmesh.openconnect.offsetmgmt.api.storage.OffsetStorageWriterImpl;
import org.apache.eventmesh.openconnect.util.CloudEventUtil;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import org.apache.commons.collections4.CollectionUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SourceWorker implements ConnectorWorker {

    private final Source source;
    private final SourceConfig config;

    private static final int MAX_RETRY_TIMES = 3;

    public static final String CALLBACK_EXTENSION = "callBackExtension";

    private OffsetStorageWriterImpl offsetStorageWriter;

    private OffsetStorageReaderImpl offsetStorageReader;

    private OffsetManagementService offsetManagementService;

    private RecordOffsetManagement offsetManagement;

    private volatile RecordOffsetManagement.CommittableOffsets committableOffsets;

    private final ExecutorService pollService =
        ThreadPoolFactory.createSingleExecutor("eventMesh-sourceWorker-pollService");

    private final ExecutorService startService =
        ThreadPoolFactory.createSingleExecutor("eventMesh-sourceWorker-startService");

    private final BlockingQueue<ConnectRecord> queue;
    private final EventMeshTCPClient<CloudEvent> eventMeshTCPClient;

    private volatile boolean isRunning = false;

    public SourceWorker(Source source, SourceConfig config) {
        this.source = source;
        this.config = config;
        queue = new LinkedBlockingQueue<>(1000);
        eventMeshTCPClient = buildEventMeshPubClient(config);
    }

    private EventMeshTCPClient<CloudEvent> buildEventMeshPubClient(SourceConfig config) {
        String meshAddress = config.getPubSubConfig().getMeshAddress();
        String meshIp = meshAddress.split(":")[0];
        int meshPort = Integer.parseInt(meshAddress.split(":")[1]);
        UserAgent agent = UserAgent.builder()
            .env(config.getPubSubConfig().getEnv())
            .host("localhost")
            .password(config.getPubSubConfig().getPassWord())
            .username(config.getPubSubConfig().getUserName())
            .group(config.getPubSubConfig().getGroup())
            .path("/")
            .port(8362)
            .subsystem(config.getPubSubConfig().getAppId())
            .pid(Integer.parseInt(SystemUtils.getProcessId()))
            .version("2.0")
            .idc(config.getPubSubConfig().getIdc())
            .build();
        UserAgent userAgent = MessageUtils.generatePubClient(agent);

        EventMeshTCPClientConfig eventMeshTcpClientConfig = EventMeshTCPClientConfig.builder()
            .host(meshIp)
            .port(meshPort)
            .userAgent(userAgent)
            .build();
        return EventMeshTCPClientFactory.createEventMeshTCPClient(eventMeshTcpClientConfig, CloudEvent.class);
    }

    @Override
    public void init() {
        SourceConnectorContext sourceConnectorContext = new SourceConnectorContext();
        sourceConnectorContext.setSourceConfig(config);
        sourceConnectorContext.setOffsetStorageReader(offsetStorageReader);
        try {
            source.init(sourceConnectorContext);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        eventMeshTCPClient.init();
        // spi load offsetMgmtService
        this.offsetManagement = new RecordOffsetManagement();
        this.committableOffsets = RecordOffsetManagement.CommittableOffsets.EMPTY;
        OffsetStorageConfig offsetStorageConfig = config.getOffsetStorageConfig();
        this.offsetManagementService = Optional.ofNullable(offsetStorageConfig)
            .map(OffsetStorageConfig::getOffsetStorageType)
            .map(storageType -> EventMeshExtensionFactory.getExtension(OffsetManagementService.class, storageType))
            .orElse(new DefaultOffsetManagementServiceImpl());
        this.offsetManagementService.initialize(offsetStorageConfig);
        this.offsetStorageWriter = new OffsetStorageWriterImpl(offsetManagementService);
        this.offsetStorageReader = new OffsetStorageReaderImpl(offsetManagementService);
    }

    @Override
    public void start() {
        log.info("source worker starting {}", source.name());
        log.info("event mesh address is {}", config.getPubSubConfig().getMeshAddress());
        // start offsetMgmtService
        offsetManagementService.start();
        isRunning = true;
        pollService.execute(this::startPollAndSend);

        startService.execute(
            () -> {
                try {
                    startConnector();
                } catch (Exception e) {
                    log.error("source worker[{}] start fail", source.name(), e);
                    this.stop();
                }
            });
    }

    public void startPollAndSend() {
        while (isRunning) {
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
            // todo: convert connectRecord to cloudevent
            CloudEvent event = convertRecordToEvent(connectRecord);
            Optional<RecordOffsetManagement.SubmittedPosition> submittedRecordPosition = prepareToUpdateRecordOffset(connectRecord);
            Optional<SendMessageCallback> callback = Optional.ofNullable(connectRecord.getExtensionObj(CALLBACK_EXTENSION))
                .map(v -> (SendMessageCallback) v);

            int retryTimes = 0;
            // retry until MAX_RETRY_TIMES is reached
            while (retryTimes < MAX_RETRY_TIMES) {
                try {
                    Package sendResult = eventMeshTCPClient.publish(event, 3000);
                    if (sendResult.getHeader().getCode() == OPStatus.SUCCESS.getCode()) {
                        // publish success
                        // commit record
                        this.source.commit(connectRecord);
                        submittedRecordPosition.ifPresent(RecordOffsetManagement.SubmittedPosition::ack);
                        callback.ifPresent(cb -> cb.onSuccess(convertToSendResult(event)));
                        break;
                    }
                    throw new EventMeshException("failed to send record.");
                } catch (Throwable t) {
                    retryTimes++;
                    log.error("{} failed to send record to {}, retry times = {}, failed record {}, throw {}",
                        this, event.getSubject(), retryTimes, connectRecord, t.getMessage());
                    callback.ifPresent(cb -> cb.onException(convertToExceptionContext(event, t)));
                }
            }

            offsetManagement.awaitAllMessages(5000, TimeUnit.MILLISECONDS);
            // update & commit offset
            updateCommittableOffsets();
            commitOffsets();
        }
    }

    private void startConnector() throws Exception {
        source.start();
        while (isRunning) {
            List<ConnectRecord> connectorRecordList = source.poll();
            if (CollectionUtils.isEmpty(connectorRecordList)) {
                continue;
            }
            for (ConnectRecord record : connectorRecordList) {
                queue.put(record);
            }
        }
    }

    private CloudEvent convertRecordToEvent(ConnectRecord connectRecord) {
        CloudEventBuilder cloudEventBuilder = CloudEventBuilder.v1();

        cloudEventBuilder.withId(UUID.randomUUID().toString())
            .withSubject(config.getPubSubConfig().getSubject())
            .withSource(URI.create("/"))
            .withDataContentType("application/cloudevents+json")
            .withType(CLOUD_EVENTS_PROTOCOL_NAME)
            .withData(Objects.requireNonNull(JsonUtils.toJSONString(connectRecord.getData())).getBytes(StandardCharsets.UTF_8))
            .withExtension("ttl", 10000);

        if (connectRecord.getExtensions() != null) {
            for (String key : connectRecord.getExtensions().keySet()) {
                if (CloudEventUtil.validateExtensionType(connectRecord.getExtensionObj(key))) {
                    cloudEventBuilder.withExtension(key, connectRecord.getExtension(key));
                }
            }
        }
        return cloudEventBuilder.build();
    }

    private SendResult convertToSendResult(CloudEvent event) {
        SendResult result = new SendResult();
        result.setMessageId(event.getId());
        result.setTopic(event.getSubject());
        return result;
    }

    private SendExcepionContext convertToExceptionContext(CloudEvent event, Throwable cause) {
        SendExcepionContext exceptionContext = new SendExcepionContext();
        exceptionContext.setTopic(event.getId());
        exceptionContext.setMessageId(event.getId());
        exceptionContext.setCause(cause);
        return exceptionContext;
    }

    @Override
    public void stop() {
        log.info("source worker stopping");
        isRunning = false;
        try {
            source.stop();
        } catch (Exception e) {
            log.error("source destroy error", e);
        }
        log.info("pollService stopping");
        pollService.shutdown();
        try {
            pollService.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error("awaitTermination error", e);
        }
        log.info("offsetMgmtService stopping");
        offsetManagementService.stop();

        try {
            log.info("eventmesh client closing");
            eventMeshTCPClient.close();
        } catch (Exception e) {
            log.error("event mesh client close error", e);
        }
        log.info("source worker stopped");
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
            log.debug("Either no records were produced since the last offset commit, "
                + "or every record has been filtered out by a transformation "
                + "or dropped due to transformation or conversion errors.");
            // We continue with the offset commit process here instead of simply returning immediately
            // in order to invoke SourceTask::commit and record metrics for a successful offset commit
        } else {
            log.info("{} Committing offsets for {} acknowledged messages", this, committableOffsets.numCommittableMessages());
            if (committableOffsets.hasPending()) {
                log.debug("{} There are currently {} pending messages spread across {} source partitions whose offsets will not be committed. "
                        + "The source partition with the most pending messages is {}, with {} pending messages",
                    this,
                    committableOffsets.numUncommittableMessages(),
                    committableOffsets.numDeques(),
                    committableOffsets.largestDequePartition(),
                    committableOffsets.largestDequeSize());
            } else {
                log.debug("{} There are currently no pending messages for this offset commit; "
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
}
