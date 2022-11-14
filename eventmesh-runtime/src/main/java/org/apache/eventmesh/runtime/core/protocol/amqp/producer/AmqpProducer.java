package org.apache.eventmesh.runtime.core.protocol.amqp.producer;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.protocol.amqp.resolver.AmqpProtocolResolver;
import org.apache.eventmesh.runtime.common.ServiceState;
import org.apache.eventmesh.runtime.configuration.EventMeshAmqpConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.consumergroup.ProducerGroupConf;
import org.apache.eventmesh.runtime.core.plugin.MQProducerWrapper;
import org.apache.eventmesh.runtime.util.EventMeshUtil;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;

public class AmqpProducer {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private ProducerGroupConf producerGroupConfig;

    private MQProducerWrapper mqProducerWrapper;

    private ServiceState serviceState;

    public void send(PutMessageContext putMessageContext, SendCallback sendCallback)
            throws Exception {
        CloudEvent ce= AmqpProtocolResolver.buildEvent(putMessageContext.getMessage());
        mqProducerWrapper.send(ce, sendCallback);
    }


    public synchronized void init(EventMeshAmqpConfiguration eventMeshAmqpConfiguration,
                                  ProducerGroupConf producerGroupConfig) throws Exception {
        this.producerGroupConfig = producerGroupConfig;

        Properties keyValue = new Properties();
        keyValue.put(EventMeshConstants.PRODUCER_GROUP, producerGroupConfig.getGroupName());
        keyValue.put(EventMeshConstants.INSTANCE_NAME, EventMeshUtil.buildMeshClientID(
                producerGroupConfig.getGroupName(), eventMeshAmqpConfiguration.eventMeshCluster));

        //TODO for defibus
        keyValue.put(EventMeshConstants.EVENT_MESH_IDC, eventMeshAmqpConfiguration.eventMeshIDC);
        mqProducerWrapper = new MQProducerWrapper(
                eventMeshAmqpConfiguration.eventMeshConnectorPluginType);
        mqProducerWrapper.init(keyValue);
        serviceState = ServiceState.INITED;
        logger.info("AmqpProducer [{}] inited...........", producerGroupConfig.getGroupName());
    }

    public synchronized void start() throws Exception {
        if (serviceState == null || ServiceState.RUNNING.equals(serviceState)) {
            return;
        }

        mqProducerWrapper.start();
        serviceState = ServiceState.RUNNING;
        logger.info("AmqpProducer [{}] started..........", producerGroupConfig.getGroupName());
    }

    public synchronized void shutdown() throws Exception {
        if (serviceState == null || ServiceState.INITED.equals(serviceState)) {
            return;
        }

        mqProducerWrapper.shutdown();
        serviceState = ServiceState.STOPED;
        logger.info("AmqpProducer [{}] shutdown.........", producerGroupConfig.getGroupName());
    }

    public ServiceState getStatus() {
        return this.serviceState;
    }












}
