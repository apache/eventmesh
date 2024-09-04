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
import org.apache.eventmesh.common.remote.JobState;
import org.apache.eventmesh.common.remote.job.JobType;
import org.apache.eventmesh.function.api.AbstractFunctionChain;
import org.apache.eventmesh.function.api.Function;
import org.apache.eventmesh.function.filter.pattern.Pattern;
import org.apache.eventmesh.function.filter.patternbuild.PatternBuilder;
import org.apache.eventmesh.function.transformer.Transformer;
import org.apache.eventmesh.function.transformer.TransformerBuilder;
import org.apache.eventmesh.function.transformer.TransformerParam;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FunctionRuntime implements Runtime {

    private final RuntimeInstanceConfig runtimeInstanceConfig;

    private final LinkedBlockingQueue<ConnectRecord> queue;

    private FunctionRuntimeConfig functionRuntimeConfig;

    private AbstractFunctionChain<String, String> functionChain;

    private Sink sinkConnector;

    private Source sourceConnector;

    private final ExecutorService sourceService = ThreadPoolFactory.createSingleExecutor("eventMesh-sourceService");

    private final ExecutorService sinkService = ThreadPoolFactory.createSingleExecutor("eventMesh-sinkService");


    private volatile boolean isRunning = false;

    private volatile boolean isFailed = false;


    public FunctionRuntime(RuntimeInstanceConfig runtimeInstanceConfig) {
        this.runtimeInstanceConfig = runtimeInstanceConfig;
        this.queue = new LinkedBlockingQueue<>(1000);
    }


    @Override
    public void init() throws Exception {
        // load function runtime config from local file
        this.functionRuntimeConfig = ConfigService.getInstance().buildConfigInstance(FunctionRuntimeConfig.class);

        // TODO init admin service

        // TODO get remote config from admin service and update local config

        // init connector service
        initConnectorService();
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
//        sourceConnectorContext.setOffsetStorageReader(offsetStorageReader);
//        if (CollectionUtils.isNotEmpty(jobResponse.getPosition())) {
//            sourceConnectorContext.setRecordPositionList(jobResponse.getPosition());
//        }
        sourceConnector.init(sourceConnectorContext);
    }

    @Override
    public void start() throws Exception {
        // build function chain
        this.functionChain = buildFunctionChain(functionRuntimeConfig.getFunctionConfigs());

        // start sink service
        sinkService.execute(() -> {
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
        sourceService.execute(() -> {
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
    }

    private StringFunctionChain buildFunctionChain(List<Map<String, Object>> functionConfigs) {
        StringFunctionChain functionChain = new StringFunctionChain();
        for (Map<String, Object> functionConfig : functionConfigs) {

            String functionType = (String) functionConfig.getOrDefault("functionType", "");
            if (functionType.isEmpty()) {
                throw new IllegalArgumentException("'functionType' is required for function");
            }

            // build function based on functionType
            Function<String, String> function;
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

    private Pattern buildFilter(Map<String, Object> functionConfig) {
        // get condition from attributes
        String condition = (String) functionConfig.get("condition");
        if (condition == null) {
            throw new IllegalArgumentException("'condition' is required for filter function");
        }
        return PatternBuilder.build(condition);
    }

    private Transformer buildTransformer(Map<String, Object> functionConfig) {
        // get transformerType from attributes
        TransformerType transformerType = TransformerType.getItem((String) functionConfig.getOrDefault("transformerType", ""));
        if (transformerType == null) {
            throw new IllegalArgumentException(
                "Invalid transformerType: '" + functionConfig.get("transformerType")
                    + "'. Supported transformerType: 'CONSTANT', 'TEMPLATE', 'ORIGINAL'");
        }

        // build transformer
        TransformerParam transformerParam = new TransformerParam();
        transformerParam.setTransformerType(transformerType);

        String value = (String) functionConfig.getOrDefault("value", "");
        String template = (String) functionConfig.getOrDefault("template", "");

        switch (transformerType) {
            case CONSTANT:
                // check value
                if (value.isEmpty()) {
                    throw new IllegalArgumentException("'value' is required for constant transformer");
                }
                transformerParam.setValue(value);
                break;
            case TEMPLATE:
                // check value and template
                if (value.isEmpty() || template.isEmpty()) {
                    throw new IllegalArgumentException("'value' and 'template' are required for template transformer");
                }
                transformerParam.setValue(value);
                transformerParam.setTemplate(template);
                break;
            default:
                // ORIGINAL doesn't need value and template
                break;
        }

        return TransformerBuilder.buildTransformer(transformerParam);
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

//        if (isFailed) {
//            reportJobRequest(connectorRuntimeConfig.getJobID(), JobState.FAIL);
//        } else {
//            reportJobRequest(connectorRuntimeConfig.getJobID(), JobState.COMPLETE);
//        }

        isRunning = false;
        sinkConnector.stop();
        sourceConnector.stop();
        sinkService.shutdown();
        sourceService.shutdown();
        log.info("FunctionRuntime stopped.");
    }
}
