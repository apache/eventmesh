package org.apache.eventmeth.protocol.http;

import com.google.common.eventbus.EventBus;
import org.apache.eventmesh.protocol.api.exception.EventMeshProtocolException;
import org.apache.eventmesh.protocol.api.model.ServiceState;
import org.apache.eventmeth.protocol.http.config.EventMeshHTTPConfiguration;
import org.apache.eventmeth.protocol.http.consumer.ConsumerGroupConf;
import org.apache.eventmeth.protocol.http.consumer.ConsumerManager;
import org.apache.eventmeth.protocol.http.model.AbstractHTTPPushRequest;
import org.apache.eventmeth.protocol.http.model.Client;
import org.apache.eventmeth.protocol.http.producer.ProducerManager;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class EventMeshProtocolHTTPServer extends AbstractEventMeshProtocolHTTPServer {

    public ServiceState serviceState;

    private EventBus eventBus = new EventBus();

    private final ConcurrentHashMap<String /**group*/, ConsumerGroupConf> localConsumerGroupMapping = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String /**group@topic*/, List<Client>> localClientInfoMapping = new ConcurrentHashMap<>();

    private ConsumerManager consumerManager;

    private ProducerManager producerManager;

    public EventMeshProtocolHTTPServer() {
        super(EventMeshHTTPConfiguration.httpServerPort, EventMeshHTTPConfiguration.eventMeshServerUseTls);
    }

    public void init() throws EventMeshProtocolException {
        logger.info("==================EventMeshHTTPServer Initialing==================");
        super.init();
        try {
            consumerManager = new ConsumerManager(this);
            consumerManager.init();

            producerManager = new ProducerManager(this);
            producerManager.init();

            logger.info("--------------------------EventMeshHTTPServer inited");
        } catch (Exception ex) {
            throw new EventMeshProtocolException(ex);
        }

    }

    @Override
    public void start() throws EventMeshProtocolException {
        super.start();
        try {
            consumerManager.start();
            producerManager.start();
            logger.info("--------------------------EventMeshHTTPServer started");
        } catch (Exception ex) {
            throw new EventMeshProtocolException(ex);
        }
    }

    @Override
    public void shutdown() throws EventMeshProtocolException {
        super.shutdown();
        try {
            producerManager.shutdown();

            consumerManager.shutdown();

            AbstractHTTPPushRequest.httpClientPool.shutdown();

            logger.info("--------------------------EventMeshHTTPServer shutdown");
        } catch (Exception ex) {
            throw new EventMeshProtocolException(ex);
        }
    }

    public ConsumerManager getConsumerManager() {
        return consumerManager;
    }

    public ProducerManager getProducerManager() {
        return producerManager;
    }

    public ServiceState getServiceState() {
        return serviceState;
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public ConcurrentHashMap<String, ConsumerGroupConf> getLocalConsumerGroupMapping() {
        return localConsumerGroupMapping;
    }

    public ConcurrentHashMap<String, List<Client>> getLocalClientInfoMapping() {
        return localClientInfoMapping;
    }

}
