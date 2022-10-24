package org.apache.eventmesh.runtime.core.protocol.amqp.consumer;

import lombok.Data;
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.EventMeshAsyncConsumeContext;
import org.apache.eventmesh.runtime.boot.EventMeshAmqpServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.plugin.MQConsumerWrapper;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import io.cloudevents.core.builder.CloudEventBuilder;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.group.ClientGroupWrapper;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * a class represent a consumer used in AMQP
 * define MQ consumer
 * define EventLister registered in MQ consumer
 */
@Data
public class ClientConsumerWrapper {
    public static Logger logger = LoggerFactory.getLogger(ClientConsumerWrapper.class);

    /**
     * group name of current consumer
     */
    private String groupName;

    /**
     * indicate whether mq consumer has been initiated or not
     */
    private AtomicBoolean initiated = new AtomicBoolean(Boolean.FALSE);

    /**
     * global server
     */
    private EventMeshAmqpServer eventMeshAmqpServer;

    /**
     * global mapping
     */
    private AmqpGlobalMapping amqpGlobalMapping;

    /**
     * a MQConsumer that used to subscribe and consume message from mesh
     */
    private MQConsumerWrapper mqConsumerWrapper;

    /**
     * strategy that used to select client to downstream message
     */
    private DownstreamDispatchStrategy downstreamDispatchStrategy;

    public ClientConsumerWrapper(EventMeshAmqpServer eventMeshAmqpServer,
                                 AmqpGlobalMapping amqpGlobalMapping,
                                 MQConsumerWrapper mqConsumerWrapper,
                                 DownstreamDispatchStrategy downstreamDispatchStrategy) {
        this.groupName = "amqpConsumerGroup";
        this.eventMeshAmqpServer = eventMeshAmqpServer;
        this.amqpGlobalMapping = amqpGlobalMapping;
        this.mqConsumerWrapper = mqConsumerWrapper;
        this.downstreamDispatchStrategy = downstreamDispatchStrategy;
    }

    /**
     * initialize mq consumer for amqp server
     * 1. initialize EventMesh mq consumer
     * 2. create EventListener and register into EventMesh mq consumer
     */
    public synchronized void init() throws Exception {
        Properties keyValue = new Properties();
        keyValue.put(EventMeshConstants.IS_BROADCAST, "false");
        keyValue.put(EventMeshConstants.EVENT_MESH_IDC, this.eventMeshAmqpServer.getEventMeshAmqpConfiguration().eventMeshIDC);
        keyValue.put(EventMeshConstants.INSTANCE_NAME, EventMeshUtil.buildMeshClientID(groupName, eventMeshAmqpServer.getEventMeshAmqpConfiguration().eventMeshCluster));
        mqConsumerWrapper.init(keyValue);

        EventListener eventListener = (cloudEvent, context) -> {
            cloudEvent = CloudEventBuilder.from(cloudEvent).withExtension(EventMeshConstants.REQ_MQ2EVENTMESH_TIMESTAMP,
                            String.valueOf(System.currentTimeMillis()))
                    .withExtension(EventMeshConstants.REQ_RECEIVE_EVENTMESH_IP,
                            this.eventMeshAmqpServer.getEventMeshAmqpConfiguration().eventMeshServerIp).build();
            String topic = cloudEvent.getSubject();
            AmqpConsumer selectedAmqpConsumer = downstreamDispatchStrategy.select(topic, amqpGlobalMapping);
            EventMeshAsyncConsumeContext eventMeshAsyncConsumeContext = (EventMeshAsyncConsumeContext) context;
            PushMessageContext pushMessageContext = new PushMessageContext(cloudEvent, this.mqConsumerWrapper, eventMeshAsyncConsumeContext.getAbstractContext());
            selectedAmqpConsumer.pushMessage(pushMessageContext);
            eventMeshAsyncConsumeContext.commit(EventMeshAction.ManualAck);
        };
        mqConsumerWrapper.registerEventListener(eventListener);
        this.initiated.compareAndSet(false, true);
        logger.info("------Initiate consumer success------");
    }
}