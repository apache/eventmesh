package org.apache.eventmesh.runtime.boot;

import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.RateLimiter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.api.meta.dto.EventMeshRegisterInfo;
import org.apache.eventmesh.api.meta.dto.EventMeshUnRegisterInfo;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.metrics.api.MetricsPluginFactory;
import org.apache.eventmesh.metrics.api.MetricsRegistry;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.consumer.SubscriptionManager;
import org.apache.eventmesh.runtime.core.protocol.http.consumer.ConsumerManager;
import org.apache.eventmesh.runtime.core.protocol.http.processor.*;
import org.apache.eventmesh.runtime.core.protocol.http.push.HTTPClientPool;
import org.apache.eventmesh.runtime.core.protocol.http.retry.HttpRetryer;
import org.apache.eventmesh.runtime.core.protocol.mcp.McpRetryer;
import org.apache.eventmesh.runtime.core.protocol.producer.ProducerManager;
import org.apache.eventmesh.runtime.meta.MetaStorage;
import org.apache.eventmesh.runtime.metrics.http.EventMeshHttpMetricsManager;
import org.assertj.core.util.Lists;

import java.util.List;
import java.util.Optional;

import static org.apache.eventmesh.common.Constants.HTTP;

/**
 * Add multiple managers to the underlying server
 */
@Slf4j
@Getter
public class EventMeshMCPServer extends AbstractMCPServer {
    private final EventMeshServer eventMeshServer;
    private final EventMeshHTTPConfiguration eventMeshHttpConfiguration;

    private final MetaStorage metaStorage;

    private final Acl acl;
    private final EventBus eventBus = new EventBus();
    private final transient HTTPClientPool httpClientPool = new HTTPClientPool(10);
    private ConsumerManager consumerManager;
    private ProducerManager producerManager;
    private SubscriptionManager subscriptionManager;
    private FilterEngine filterEngine;
    private TransformerEngine transformerEngine;
    private McpRetryer mcpRetryer;
    private transient RateLimiter msgRateLimiter;
    private transient RateLimiter batchRateLimiter;

    public EventMeshMCPServer(final EventMeshServer eventMeshServer, final EventMeshHTTPConfiguration eventMeshHttpConfiguration) {
        super(eventMeshHttpConfiguration.getHttpServerPort(),
                eventMeshHttpConfiguration.isEventMeshServerUseTls(),
                eventMeshHttpConfiguration);
        this.eventMeshServer = eventMeshServer;
        this.eventMeshHttpConfiguration = eventMeshHttpConfiguration;
        this.metaStorage = eventMeshServer.getMetaStorage();
        this.acl = eventMeshServer.getAcl();
    }

    public void init() throws Exception {
        log.info("==================EventMeshMcpServer Initialing==================");
        super.init();

        msgRateLimiter = RateLimiter.create(eventMeshHttpConfiguration.getEventMeshHttpMsgReqNumPerSecond());
        batchRateLimiter = RateLimiter.create(eventMeshHttpConfiguration.getEventMeshBatchMsgRequestNumPerSecond());

        // The MetricsRegistry is singleton, so we can use factory method to get.
        final List<MetricsRegistry> metricsRegistries = Lists.newArrayList();
        Optional.ofNullable(eventMeshHttpConfiguration.getEventMeshMetricsPluginType()).ifPresent(
                metricsPlugins -> metricsPlugins.forEach(
                        pluginType -> metricsRegistries.add(MetricsPluginFactory.getMetricsRegistry(pluginType))));

        mcpRetryer = new McpRetryer(this);

        super.setEventMeshHttpMetricsManager(new EventMeshHttpMetricsManager(this, metricsRegistries));
        subscriptionManager = new SubscriptionManager(eventMeshHttpConfiguration.isEventMeshServerMetaStorageEnable(), metaStorage);

        consumerManager = new ConsumerManager(this);
        consumerManager.init();

        producerManager = new ProducerManager(this);
        producerManager.init();

        filterEngine = new FilterEngine(metaStorage, producerManager, consumerManager);

        transformerEngine = new TransformerEngine(metaStorage, producerManager, consumerManager);

        super.setHandlerService(new HandlerService());
        super.getHandlerService().setMetrics(this.getEventMeshHttpMetricsManager());

        // get the trace-plugin
        if (StringUtils.isNotEmpty(eventMeshHttpConfiguration.getEventMeshTracePluginType())
                && eventMeshHttpConfiguration.isEventMeshServerTraceEnable()) {
            super.setUseTrace(eventMeshHttpConfiguration.isEventMeshServerTraceEnable());
        }
        super.getHandlerService().setHttpTrace(new HTTPTrace(eventMeshHttpConfiguration.isEventMeshServerTraceEnable()));

        registerHTTPRequestProcessor();

        log.info("==================EventMeshHTTPServer initialized==================");
    }

    @Override
    public void start() throws Exception {
        super.start();
        this.getEventMeshHttpMetricsManager().start();

        consumerManager.start();
        producerManager.start();
        mcpRetryer.start();
        // filterEngine depend on metaStorage
        if (metaStorage.getStarted().get()) {
            filterEngine.start();
        }

        if (eventMeshHttpConfiguration.isEventMeshServerMetaStorageEnable()) {
            this.register();
        }
        log.info("==================EventMeshHTTPServer started==================");
    }

    @Override
    public void shutdown() throws Exception {

        super.shutdown();

        this.getEventMeshHttpMetricsManager().shutdown();

        filterEngine.shutdown();

        transformerEngine.shutdown();

        consumerManager.shutdown();

        httpClientPool.shutdown();

        producerManager.shutdown();

        mcpRetryer.shutdown();

        if (eventMeshHttpConfiguration.isEventMeshServerMetaStorageEnable()) {
            this.unRegister();
        }
        log.info("==================EventMeshHTTPServer shutdown==================");
    }

    /**
     * Related to the registry module
     */
    public boolean register() {
        boolean registerResult = false;
        try {
            final String endPoints = IPUtils.getLocalAddress()
                    + EventMeshConstants.IP_PORT_SEPARATOR + eventMeshHttpConfiguration.getHttpServerPort();
            final EventMeshRegisterInfo eventMeshRegisterInfo = new EventMeshRegisterInfo();
            eventMeshRegisterInfo.setEventMeshClusterName(eventMeshHttpConfiguration.getEventMeshCluster());
            eventMeshRegisterInfo.setEventMeshName(eventMeshHttpConfiguration.getEventMeshName()
                    + "-" + HTTP);
            eventMeshRegisterInfo.setEndPoint(endPoints);
            eventMeshRegisterInfo.setProtocolType(HTTP);
            registerResult = metaStorage.register(eventMeshRegisterInfo);
        } catch (Exception e) {
            log.error("eventMesh register to registry failed", e);
        }

        return registerResult;
    }

    /**
     * Related to the registry module
     */
    private void unRegister() {
        final String endPoints = IPUtils.getLocalAddress()
                + EventMeshConstants.IP_PORT_SEPARATOR + eventMeshHttpConfiguration.getHttpServerPort();
        final EventMeshUnRegisterInfo eventMeshUnRegisterInfo = new EventMeshUnRegisterInfo();
        eventMeshUnRegisterInfo.setEventMeshClusterName(eventMeshHttpConfiguration.getEventMeshCluster());
        eventMeshUnRegisterInfo.setEventMeshName(eventMeshHttpConfiguration.getEventMeshName());
        eventMeshUnRegisterInfo.setEndPoint(endPoints);
        eventMeshUnRegisterInfo.setProtocolType(HTTP);
        final boolean registerResult = metaStorage.unRegister(eventMeshUnRegisterInfo);
        if (!registerResult) {
            throw new EventMeshException("eventMesh fail to unRegister");
        }
    }

    private void registerHTTPRequestProcessor() throws Exception {
        final BatchSendMessageProcessor batchSendMessageProcessor = new BatchSendMessageProcessor(this);
        registerProcessor(RequestCode.MSG_BATCH_SEND.getRequestCode(), batchSendMessageProcessor);
    }
}
