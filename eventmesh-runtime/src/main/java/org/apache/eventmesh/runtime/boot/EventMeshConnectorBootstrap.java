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

package org.apache.eventmesh.runtime.boot;

import org.apache.eventmesh.api.AsyncConsumeContext;
import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.config.connector.SinkConfig;
import org.apache.eventmesh.common.config.connector.SourceConfig;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.function.api.Router;
import org.apache.eventmesh.openconnect.Application;
import org.apache.eventmesh.openconnect.ConnectorWorker;
import org.apache.eventmesh.openconnect.SinkWorker;
import org.apache.eventmesh.openconnect.SourceWorker;
import org.apache.eventmesh.openconnect.api.connector.Connector;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.openconnect.offsetmgmt.api.callback.SendExceptionContext;
import org.apache.eventmesh.openconnect.offsetmgmt.api.callback.SendResult;
import org.apache.eventmesh.openconnect.util.ConfigUtil;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.EgressProcessor;
import org.apache.eventmesh.runtime.core.protocol.IngressProcessor;
import org.apache.eventmesh.runtime.core.plugin.MQConsumerWrapper;
import org.apache.eventmesh.runtime.core.plugin.MQProducerWrapper;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import java.util.Properties;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshConnectorBootstrap implements EventMeshBootstrap {

    private final EventMeshServer eventMeshServer;
    private ConnectorWorker worker;
    private Connector connector;
    private MQProducerWrapper producer;
    private MQConsumerWrapper consumer;
    private IngressProcessor ingressProcessor;
    private EgressProcessor egressProcessor;

    public EventMeshConnectorBootstrap(EventMeshServer eventMeshServer) {
        this.eventMeshServer = eventMeshServer;
    }

    @Override
    public void init() throws Exception {
        CommonConfiguration config = eventMeshServer.getConfiguration();
        if (!config.isEventMeshConnectorPluginEnable()) {
            return;
        }
        
        this.ingressProcessor = new IngressProcessor(
            eventMeshServer.getFilterEngine(),
            eventMeshServer.getTransformerEngine(),
            eventMeshServer.getRouterEngine()
        );
        this.egressProcessor = new EgressProcessor(
            eventMeshServer.getFilterEngine(),
            eventMeshServer.getTransformerEngine()
        );

        String type = config.getEventMeshConnectorPluginType();
        String name = config.getEventMeshConnectorPluginName();

        if ("source".equalsIgnoreCase(type)) {
            connector = EventMeshExtensionFactory.getExtension(Source.class, name);
        } else if ("sink".equalsIgnoreCase(type)) {
            connector = EventMeshExtensionFactory.getExtension(Sink.class, name);
        }

        if (connector == null) {
            log.error("Connector not found: type={}, name={}", type, name);
            return;
        }

        Config connectorConfig = ConfigUtil.parse(connector.configClass());

        if (Application.isSink(connector.getClass())) {
            worker = new SinkWorker((Sink) connector, (SinkConfig) connectorConfig);
            ((SinkWorker) worker).setEmbedded(true);

            SinkConfig sinkConfig = (SinkConfig) connectorConfig;
            consumer = new MQConsumerWrapper(config.getEventMeshStoragePluginType());
            Properties props = new Properties();
            props.put(EventMeshConstants.CONSUMER_GROUP, sinkConfig.getPubSubConfig().getGroup());
            props.put(EventMeshConstants.INSTANCE_NAME, EventMeshUtil.buildMeshClientID(
                sinkConfig.getPubSubConfig().getGroup(), config.getEventMeshCluster()));
            props.put(EventMeshConstants.EVENT_MESH_IDC, config.getEventMeshIDC());
            consumer.init(props);
            
            consumer.subscribe(sinkConfig.getPubSubConfig().getSubject());

            consumer.registerEventListener(new EventListener() {
                @Override
                public void consume(CloudEvent event, AsyncConsumeContext context) {
                    try {
                        // 1. Egress Pipeline
                        String pipelineKey = sinkConfig.getPubSubConfig().getGroup() + "-" + event.getSubject();
                        event = egressProcessor.process(event, pipelineKey);
                        
                        if (event == null) {
                            context.commit(EventMeshAction.CommitMessage);
                            return;
                        }

                        ((SinkWorker) worker).handle(event);
                        context.commit(EventMeshAction.CommitMessage);
                    } catch (Exception e) {
                        log.error("Error in Sink processing", e);
                        context.commit(EventMeshAction.ReconsumeLater);
                    }
                }
            });

        } else if (Application.isSource(connector.getClass())) {
            worker = new SourceWorker((Source) connector, (SourceConfig) connectorConfig);
            
            // Initialize Producer for Source
            SourceConfig sourceConfig = (SourceConfig) connectorConfig;
            producer = new MQProducerWrapper(config.getEventMeshStoragePluginType());
            Properties props = new Properties();
            props.put(EventMeshConstants.PRODUCER_GROUP, sourceConfig.getPubSubConfig().getGroup());
            props.put(EventMeshConstants.INSTANCE_NAME, EventMeshUtil.buildMeshClientID(
                    sourceConfig.getPubSubConfig().getGroup(), config.getEventMeshCluster()));
            props.put(EventMeshConstants.EVENT_MESH_IDC, config.getEventMeshIDC());
            producer.init(props);
            
            ((SourceWorker) worker).setPublisher((event, callback) -> {
                try {
                    // 1. Ingress Pipeline
                    String pipelineKey = sourceConfig.getPubSubConfig().getGroup() + "-" + event.getSubject();
                    event = ingressProcessor.process(event, pipelineKey);
                    
                    if (event == null) {
                        SendResult result = new SendResult();
                        result.setTopic(event.getSubject());
                        result.setMessageId(event.getId());
                        callback.onSuccess(result);
                        return;
                    }

                    // 4. Storage
                    final CloudEvent finalEvent = event;
                    producer.send(finalEvent, new SendCallback() {
                        @Override
                        public void onSuccess(org.apache.eventmesh.api.SendResult sendResult) {
                            SendResult res = new SendResult();
                            res.setTopic(sendResult.getTopic());
                            res.setMessageId(sendResult.getMessageId());
                            callback.onSuccess(res);
                        }

                        @Override
                        public void onException(OnExceptionContext context) {
                            SendExceptionContext ctx = new SendExceptionContext();
                            ctx.setCause(context.getException());
                            callback.onException(ctx);
                        }
                    });
                } catch (Exception e) {
                    SendExceptionContext ctx = new SendExceptionContext();
                    ctx.setCause(e);
                    callback.onException(ctx);
                }
            });
        } else {
            log.error("class {} is not sink and source", connector.getClass());
            return;
        }

        if (worker != null) {
            worker.init();
        }
    }

    @Override
    public void start() throws Exception {
        if (producer != null) {
            producer.start();
        }
        if (consumer != null) {
            consumer.start();
        }
        if (worker != null) {
            worker.start();
        }
    }

    @Override
    public void shutdown() throws Exception {
        if (worker != null) {
            worker.stop();
        }
        if (producer != null) {
            producer.shutdown();
        }
        if (consumer != null) {
            consumer.shutdown();
        }
    }
}
