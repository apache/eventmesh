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

package org.apache.eventmesh.runtime.function;

import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.common.config.ConfigService;
import org.apache.eventmesh.common.config.connector.SinkConfig;
import org.apache.eventmesh.common.config.connector.SourceConfig;
import org.apache.eventmesh.common.protocol.grpc.adminserver.AdminServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.adminserver.AdminServiceGrpc.AdminServiceBlockingStub;
import org.apache.eventmesh.common.protocol.grpc.adminserver.AdminServiceGrpc.AdminServiceStub;
import org.apache.eventmesh.common.protocol.grpc.adminserver.Metadata;
import org.apache.eventmesh.common.protocol.grpc.adminserver.Payload;
import org.apache.eventmesh.common.remote.JobState;
import org.apache.eventmesh.common.remote.exception.ErrorCode;
import org.apache.eventmesh.common.remote.job.JobType;
import org.apache.eventmesh.common.remote.request.FetchJobRequest;
import org.apache.eventmesh.common.remote.request.ReportHeartBeatRequest;
import org.apache.eventmesh.common.remote.request.ReportJobRequest;
import org.apache.eventmesh.common.remote.response.FetchJobResponse;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.function.api.AbstractEventMeshFunctionChain;
import org.apache.eventmesh.function.api.EventMeshFunction;
import org.apache.eventmesh.function.filter.pattern.Pattern;
import org.apache.eventmesh.function.filter.patternbuild.PatternBuilder;
import org.apache.eventmesh.function.transformer.Transformer;
import org.apache.eventmesh.function.transformer.TransformerBuilder;
import org.apache.eventmesh.function.transformer.TransformerType;
import org.apache.eventmesh.openconnect.api.ConnectorCreateService;
import org.apache.eventmesh.openconnect.api.connector.SinkConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SourceConnectorContext;
import org.apache.eventmesh.openconnect.api.factory.ConnectorPluginFactory;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.util.ConfigUtil;
import org.apache.eventmesh.runtime.Runtime;
import org.apache.eventmesh.runtime.RuntimeInstanceConfig;

import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import com.google.protobuf.Any;
import com.google.protobuf.UnsafeByteOperations;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FunctionRuntime implements Runtime {

    private final RuntimeInstanceConfig runtimeInstanceConfig;

    private ManagedChannel channel;

    private AdminServiceStub adminServiceStub;

    private AdminServiceBlockingStub adminServiceBlockingStub;

    StreamObserver<Payload> responseObserver;

    StreamObserver<Payload> requestObserver;

    private final LinkedBlockingQueue<ConnectRecord> queue;

    private FunctionRuntimeConfig functionRuntimeConfig;

    private AbstractEventMeshFunctionChain<String, String> functionChain;

    private Sink sinkConnector;

    private Source sourceConnector;

    private final ExecutorService sourceService = ThreadPoolFactory.createSingleExecutor("eventMesh-sourceService");

    private final ExecutorService sinkService = ThreadPoolFactory.createSingleExecutor("eventMesh-sinkService");

    private final ScheduledExecutorService heartBeatExecutor = Executors.newSingleThreadScheduledExecutor();

    private volatile boolean isRunning = false;

    private volatile boolean isFailed = false;

    private String adminServerAddr;


    public FunctionRuntime(RuntimeInstanceConfig runtimeInstanceConfig) {
        this.runtimeInstanceConfig = runtimeInstanceConfig;
        this.queue = new LinkedBlockingQueue<>(1000);
    }


    @Override
    public void init() throws Exception {
        // load function runtime config from local file
        this.functionRuntimeConfig = ConfigService.getInstance().buildConfigInstance(FunctionRuntimeConfig.class);

        // init admin service
        initAdminService();

        // get remote config from admin service and update local config
        getAndUpdateRemoteConfig();

        // init connector service
        initConnectorService();

        // report status to admin server
        reportJobRequest(functionRuntimeConfig.getJobID(), JobState.INIT);
    }

    private void initAdminService() {
        adminServerAddr = getRandomAdminServerAddr(runtimeInstanceConfig.getAdminServiceAddr());
        // create gRPC channel
        channel = ManagedChannelBuilder.forTarget(adminServerAddr).usePlaintext().build();

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

    private String getRandomAdminServerAddr(String adminServerAddrList) {
        String[] addresses = adminServerAddrList.split(";");
        if (addresses.length == 0) {
            throw new IllegalArgumentException("Admin server address list is empty");
        }
        Random random = new Random();
        int randomIndex = random.nextInt(addresses.length);
        return addresses[randomIndex];
    }

    private void getAndUpdateRemoteConfig() {
        String jobId = functionRuntimeConfig.getJobID();
        FetchJobRequest jobRequest = new FetchJobRequest();
        jobRequest.setJobID(jobId);

        Metadata metadata = Metadata.newBuilder().setType(FetchJobRequest.class.getSimpleName()).build();

        Payload request = Payload.newBuilder().setMetadata(metadata)
            .setBody(Any.newBuilder().setValue(UnsafeByteOperations.unsafeWrap(Objects.requireNonNull(JsonUtils.toJSONBytes(jobRequest)))).build())
            .build();
        Payload response = adminServiceBlockingStub.invoke(request);
        FetchJobResponse jobResponse = null;
        if (response.getMetadata().getType().equals(FetchJobResponse.class.getSimpleName())) {
            jobResponse = JsonUtils.parseObject(response.getBody().getValue().toStringUtf8(), FetchJobResponse.class);
        }

        if (jobResponse == null || jobResponse.getErrorCode() != ErrorCode.SUCCESS) {
            if (jobResponse != null) {
                log.error("Failed to get remote config from admin server. ErrorCode: {}, Response: {}",
                    jobResponse.getErrorCode(), jobResponse);
            } else {
                log.error("Failed to get remote config from admin server. ");
            }
            isFailed = true;
            try {
                stop();
            } catch (Exception e) {
                log.error("Failed to stop after exception", e);
            }
            throw new RuntimeException("Failed to get remote config from admin server.");
        }

        // update local config
        // source
        functionRuntimeConfig.setSourceConnectorType(jobResponse.getTransportType().getSrc().getName());
        functionRuntimeConfig.setSourceConnectorDesc(jobResponse.getConnectorConfig().getSourceConnectorDesc());
        functionRuntimeConfig.setSourceConnectorConfig(jobResponse.getConnectorConfig().getSourceConnectorConfig());

        // sink
        functionRuntimeConfig.setSinkConnectorType(jobResponse.getTransportType().getDst().getName());
        functionRuntimeConfig.setSinkConnectorDesc(jobResponse.getConnectorConfig().getSinkConnectorDesc());
        functionRuntimeConfig.setSinkConnectorConfig(jobResponse.getConnectorConfig().getSinkConnectorConfig());

        // function
        functionRuntimeConfig.setFunctionConfigs(jobResponse.getFunctionConfigs());
    }


    private void initConnectorService() throws Exception {
        final JobType jobType = (JobType) functionRuntimeConfig.getRuntimeConfig().get("jobType");

        // create sink connector
        ConnectorCreateService<?> sinkConnectorCreateService =
            ConnectorPluginFactory.createConnector(functionRuntimeConfig.getSinkConnectorType() + "-Sink");
        this.sinkConnector = (Sink) sinkConnectorCreateService.create();

        // parse sink config and init sink connector
        SinkConfig sinkConfig = (SinkConfig) ConfigUtil.parse(functionRuntimeConfig.getSinkConnectorConfig(), sinkConnector.configClass());
        SinkConnectorContext sinkConnectorContext = new SinkConnectorContext();
        sinkConnectorContext.setSinkConfig(sinkConfig);
        sinkConnectorContext.setRuntimeConfig(functionRuntimeConfig.getRuntimeConfig());
        sinkConnectorContext.setJobType(jobType);
        sinkConnector.init(sinkConnectorContext);

        // create source connector
        ConnectorCreateService<?> sourceConnectorCreateService =
            ConnectorPluginFactory.createConnector(functionRuntimeConfig.getSourceConnectorType() + "-Source");
        this.sourceConnector = (Source) sourceConnectorCreateService.create();

        // parse source config and init source connector
        SourceConfig sourceConfig = (SourceConfig) ConfigUtil.parse(functionRuntimeConfig.getSourceConnectorConfig(), sourceConnector.configClass());
        SourceConnectorContext sourceConnectorContext = new SourceConnectorContext();
        sourceConnectorContext.setSourceConfig(sourceConfig);
        sourceConnectorContext.setRuntimeConfig(functionRuntimeConfig.getRuntimeConfig());
        sourceConnectorContext.setJobType(jobType);

        sourceConnector.init(sourceConnectorContext);
    }

    private void reportJobRequest(String jobId, JobState jobState) throws InterruptedException {
        ReportJobRequest reportJobRequest = new ReportJobRequest();
        reportJobRequest.setJobID(jobId);
        reportJobRequest.setState(jobState);
        Metadata metadata = Metadata.newBuilder()
            .setType(ReportJobRequest.class.getSimpleName())
            .build();
        Payload payload = Payload.newBuilder()
            .setMetadata(metadata)
            .setBody(Any.newBuilder().setValue(UnsafeByteOperations.unsafeWrap(Objects.requireNonNull(JsonUtils.toJSONBytes(reportJobRequest))))
                .build())
            .build();
        requestObserver.onNext(payload);
    }


    @Override
    public void start() throws Exception {
        this.isRunning = true;

        // build function chain
        this.functionChain = buildFunctionChain(functionRuntimeConfig.getFunctionConfigs());

        // start heart beat
        this.heartBeatExecutor.scheduleAtFixedRate(() -> {

            ReportHeartBeatRequest heartBeat = new ReportHeartBeatRequest();
            heartBeat.setAddress(IPUtils.getLocalAddress());
            heartBeat.setReportedTimeStamp(String.valueOf(System.currentTimeMillis()));
            heartBeat.setJobID(functionRuntimeConfig.getJobID());

            Metadata metadata = Metadata.newBuilder().setType(ReportHeartBeatRequest.class.getSimpleName()).build();

            Payload request = Payload.newBuilder().setMetadata(metadata)
                .setBody(Any.newBuilder().setValue(UnsafeByteOperations.unsafeWrap(Objects.requireNonNull(JsonUtils.toJSONBytes(heartBeat)))).build())
                .build();

            requestObserver.onNext(request);
        }, 5, 5, TimeUnit.SECONDS);

        // start sink service
        this.sinkService.execute(() -> {
            try {
                startSinkConnector();
            } catch (Exception e) {
                isFailed = true;
                log.error("Sink Connector [{}] failed to start.", sinkConnector.name(), e);
                try {
                    this.stop();
                } catch (Exception ex) {
                    log.error("Failed to stop after exception", ex);
                }
                throw new RuntimeException(e);
            }
        });

        // start source service
        this.sourceService.execute(() -> {
            try {
                startSourceConnector();
            } catch (Exception e) {
                isFailed = true;
                log.error("Source Connector [{}] failed to start.", sourceConnector.name(), e);
                try {
                    this.stop();
                } catch (Exception ex) {
                    log.error("Failed to stop after exception", ex);
                }
                throw new RuntimeException(e);
            }
        });

        reportJobRequest(functionRuntimeConfig.getJobID(), JobState.RUNNING);
    }

    private StringEventMeshFunctionChain buildFunctionChain(List<Map<String, Object>> functionConfigs) {
        StringEventMeshFunctionChain functionChain = new StringEventMeshFunctionChain();

        // build function chain
        for (Map<String, Object> functionConfig : functionConfigs) {
            String functionType = String.valueOf(functionConfig.getOrDefault("functionType", ""));
            if (StringUtils.isEmpty(functionType)) {
                throw new IllegalArgumentException("'functionType' is required for function");
            }

            // build function based on functionType
            EventMeshFunction<String, String> function;
            switch (functionType) {
                case "filter":
                    function = buildFilter(functionConfig);
                    break;
                case "transformer":
                    function = buildTransformer(functionConfig);
                    break;
                default:
                    throw new IllegalArgumentException(
                        "Invalid functionType: '" + functionType + "'. Supported functionType: 'filter', 'transformer'");
            }

            // add function to functionChain
            functionChain.addLast(function);
        }

        return functionChain;
    }


    @SuppressWarnings("unchecked")
    private Pattern buildFilter(Map<String, Object> functionConfig) {
        // get condition from attributes
        Object condition = functionConfig.get("condition");
        if (condition == null) {
            throw new IllegalArgumentException("'condition' is required for filter function");
        }
        if (condition instanceof String) {
            return PatternBuilder.build(String.valueOf(condition));
        } else if (condition instanceof Map) {
            return PatternBuilder.build((Map<String, Object>) condition);
        } else {
            throw new IllegalArgumentException("Invalid condition");
        }
    }

    private Transformer buildTransformer(Map<String, Object> functionConfig) {
        // get transformerType from attributes
        String transformerTypeStr = String.valueOf(functionConfig.getOrDefault("transformerType", "")).toLowerCase();
        TransformerType transformerType = TransformerType.getItem(transformerTypeStr);
        if (transformerType == null) {
            throw new IllegalArgumentException(
                "Invalid transformerType: '" + transformerTypeStr
                    + "'. Supported transformerType: 'constant', 'template', 'original' (case insensitive)");
        }

        // build transformer
        Transformer transformer = null;

        switch (transformerType) {
            case CONSTANT:
                // check value
                String content = String.valueOf(functionConfig.getOrDefault("content", ""));
                if (StringUtils.isEmpty(content)) {
                    throw new IllegalArgumentException("'content' is required for constant transformer");
                }
                transformer = TransformerBuilder.buildConstantTransformer(content);
                break;
            case TEMPLATE:
                // check value and template
                Object valueMap = functionConfig.get("valueMap");
                String template = String.valueOf(functionConfig.getOrDefault("template", ""));
                if (valueMap == null || StringUtils.isEmpty(template)) {
                    throw new IllegalArgumentException("'valueMap' and 'template' are required for template transformer");
                }
                transformer = TransformerBuilder.buildTemplateTransFormer(valueMap, template);
                break;
            case ORIGINAL:
                // ORIGINAL transformer does not need any parameter
                break;
            default:
                throw new IllegalArgumentException(
                    "Invalid transformerType: '" + transformerType + "', supported transformerType: 'CONSTANT', 'TEMPLATE', 'ORIGINAL'");
        }

        return transformer;
    }


    private void startSinkConnector() throws Exception {
        // start sink connector
        this.sinkConnector.start();

        // try to get data from queue and send it.
        while (this.isRunning) {
            ConnectRecord connectRecord = null;
            try {
                connectRecord = queue.poll(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                log.error("Failed to poll data from queue.", e);
                Thread.currentThread().interrupt();
            }

            // send data if not null
            if (connectRecord != null) {
                sinkConnector.put(Collections.singletonList(connectRecord));
            }
        }
    }

    private void startSourceConnector() throws Exception {
        // start source connector
        this.sourceConnector.start();

        // try to get data from source connector and handle it.
        while (this.isRunning) {
            List<ConnectRecord> connectorRecordList = sourceConnector.poll();

            // handle data
            if (connectorRecordList != null && !connectorRecordList.isEmpty()) {
                for (ConnectRecord connectRecord : connectorRecordList) {
                    if (connectRecord == null || connectRecord.getData() == null) {
                        // If data is null, just put it into queue.
                        this.queue.put(connectRecord);
                    } else {
                        // Apply function chain to data
                        String data = functionChain.apply((String) connectRecord.getData());
                        if (data != null) {
                            connectRecord.setData(data);
                            this.queue.put(connectRecord);
                        }
                    }
                }
            }
        }
    }


    @Override
    public void stop() throws Exception {
        log.info("FunctionRuntime is stopping...");

        isRunning = false;

        if (isFailed) {
            reportJobRequest(functionRuntimeConfig.getJobID(), JobState.FAIL);
        } else {
            reportJobRequest(functionRuntimeConfig.getJobID(), JobState.COMPLETE);
        }

        sinkConnector.stop();
        sourceConnector.stop();
        sinkService.shutdown();
        sourceService.shutdown();
        heartBeatExecutor.shutdown();

        requestObserver.onCompleted();
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
        }

        log.info("FunctionRuntime stopped.");
    }
}
