package org.apache.eventmesh.runtime.connector;

import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.openconnect.api.ConnectorCreateService;
import org.apache.eventmesh.openconnect.api.config.Config;
import org.apache.eventmesh.openconnect.api.config.SinkConfig;
import org.apache.eventmesh.openconnect.api.config.SourceConfig;
import org.apache.eventmesh.openconnect.api.connector.Connector;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SinkConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SourceConnectorContext;
import org.apache.eventmesh.openconnect.api.factory.ConnectorPluginFactory;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.util.ConfigUtil;
import org.apache.eventmesh.runtime.Runtime;
import org.apache.eventmesh.runtime.RuntimeInstanceConfig;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConnectorRuntime implements Runtime {

    private RuntimeInstanceConfig runtimeInstanceConfig;

    private ConnectorRuntimeConfig connectorRuntimeConfig;

    private Source sourceConnector;

    private Sink sinkConnector;

    private final ExecutorService sourceService =
        ThreadPoolFactory.createSingleExecutor("eventMesh-sourceService");

    private final ExecutorService startService =
        ThreadPoolFactory.createSingleExecutor("eventMesh-sourceWorker-startService");

    private BlockingQueue<ConnectRecord> queue;

    private volatile boolean isRunning = false;


    public ConnectorRuntime(RuntimeInstanceConfig runtimeInstanceConfig) {
        this.runtimeInstanceConfig = runtimeInstanceConfig;
        this.queue = new LinkedBlockingQueue<>(1000);
    }

    @Override
    public void init() throws Exception {
        ConnectorCreateService<?> sourceConnectorCreateService = ConnectorPluginFactory.createConnector(
            connectorRuntimeConfig.getSourceConnectorType());
        sourceConnector = (Source)sourceConnectorCreateService.create();

        SourceConfig sourceConfig = (SourceConfig) ConfigUtil.parse(connectorRuntimeConfig.getSourceConnectorConfig(), sourceConnector.configClass());
        SourceConnectorContext sourceConnectorContext = new SourceConnectorContext();
        sourceConnectorContext.setSourceConfig(sourceConfig);
//        sourceConnectorContext.setOffsetStorageReader(offsetStorageReader);

        sourceConnector.init(sourceConnectorContext);

        ConnectorCreateService<?> sinkConnectorCreateService = ConnectorPluginFactory.createConnector(connectorRuntimeConfig.getSinkConnectorType());
        sinkConnector = (Sink)sinkConnectorCreateService.create();

        SinkConfig sinkConfig = (SinkConfig) ConfigUtil.parse(connectorRuntimeConfig.getSinkConnectorConfig(), sinkConnector.configClass());
        SinkConnectorContext sinkConnectorContext = new SinkConnectorContext();
        sinkConnectorContext.setSinkConfig(sinkConfig);
        sinkConnector.init(sinkConnectorContext);
    }

    @Override
    public void start() throws Exception {

//        sourceConnector.start();
//        sourceConnector.poll();
// start offsetMgmtService
//        offsetManagementService.start();
        isRunning = true;
//        pollService.execute(this::startPollAndSend);

        sourceService.execute(
            () -> {
                try {
                    startSourceConnector();
                } catch (Exception e) {
                    log.error("source connector [{}] start fail", sourceConnector.name(), e);
//                    this.stop();
                }
            });


    }

    @Override
    public void stop() throws Exception {

    }

    private void startSourceConnector() throws Exception {
        sourceConnector.start();
        while (isRunning) {
            List<ConnectRecord> connectorRecordList = sourceConnector.poll();
            if (connectorRecordList != null && !connectorRecordList.isEmpty()) {
                for (ConnectRecord record : connectorRecordList) {
                    queue.put(record);
                }
            }
        }
    }
}
