package org.apache.eventmesh.runtime.core.protocol.grpc.consumer;

import com.google.common.collect.Maps;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.EventMeshAsyncConsumeContext;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription.SubscriptionItem.SubscriptionMode;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.common.ServiceState;
import org.apache.eventmesh.runtime.configuration.EventMeshGrpcConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.plugin.MQConsumerWrapper;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.ConsumerGroupTopicConfig;
import org.apache.eventmesh.runtime.core.protocol.grpc.push.HTTPMessageHandler;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class EventMeshConsumer {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final String consumerGroup;

    private final EventMeshGrpcServer eventMeshGrpcServer;

    private final EventMeshGrpcConfiguration eventMeshGrpcConfiguration;

    private final MQConsumerWrapper persistentMqConsumer;

    private final MQConsumerWrapper broadcastMqConsumer;

    private final HTTPMessageHandler httpMessageHandler;

    private ServiceState serviceState;

    /**
     * Key: topic
     * Value: ConsumerGroupTopicConfig
     **/
    private Map<String, ConsumerGroupTopicConfig> consumerGroupTopicConfig = Maps.newConcurrentMap();

    public EventMeshConsumer(EventMeshGrpcServer eventMeshGrpcServer, String consumerGroup) {
        this.eventMeshGrpcServer = eventMeshGrpcServer;
        this.eventMeshGrpcConfiguration = eventMeshGrpcServer.getEventMeshGrpcConfiguration();
        this.consumerGroup = consumerGroup;
        this.httpMessageHandler = new HTTPMessageHandler(consumerGroup, eventMeshGrpcServer.getPushMsgExecutor());
        this.persistentMqConsumer = new MQConsumerWrapper(eventMeshGrpcConfiguration.eventMeshConnectorPluginType);
        this.broadcastMqConsumer = new MQConsumerWrapper(eventMeshGrpcConfiguration.eventMeshConnectorPluginType);
    }

    public synchronized void addTopicConfig(String topic, SubscriptionMode subscriptionMode, String idc, String url) {
        ConsumerGroupTopicConfig topicConfig = consumerGroupTopicConfig.get(topic);
        if (topicConfig == null) {
            topicConfig = new ConsumerGroupTopicConfig(consumerGroup, topic, subscriptionMode);
            consumerGroupTopicConfig.put(topic, topicConfig);
        }
        topicConfig.addUrl(idc, url);
    }

    public Set<String> buildTopicConfig() {
        Set<String> topicConfigs = new HashSet<>();
        for (ConsumerGroupTopicConfig topicConfig : consumerGroupTopicConfig.values()) {
            topicConfigs.add(topicConfig.getTopic() + topicConfig.getSubscriptionMode().name());
        }
        return topicConfigs;
    }

    public synchronized void init() throws Exception {
        Properties keyValue = new Properties();
        keyValue.put("isBroadcast", "false");
        keyValue.put("consumerGroup", consumerGroup);
        keyValue.put("eventMeshIDC", eventMeshGrpcConfiguration.eventMeshIDC);
        keyValue.put("instanceName", EventMeshUtil.buildMeshClientID(consumerGroup,
            eventMeshGrpcConfiguration.eventMeshCluster));
        persistentMqConsumer.init(keyValue);

        Properties broadcastKeyValue = new Properties();
        broadcastKeyValue.put("isBroadcast", "true");
        broadcastKeyValue.put("consumerGroup", consumerGroup);
        broadcastKeyValue.put("eventMeshIDC", eventMeshGrpcConfiguration.eventMeshIDC);
        broadcastKeyValue.put("instanceName", EventMeshUtil.buildMeshClientID(consumerGroup,
            eventMeshGrpcConfiguration.eventMeshCluster));
        broadcastMqConsumer.init(broadcastKeyValue);

        serviceState = ServiceState.INITED;
        logger.info("EventMeshConsumer [{}] initialized.............", consumerGroup);
    }

    public synchronized void start() throws Exception {
        for (Map.Entry<String, ConsumerGroupTopicConfig> entry : consumerGroupTopicConfig.entrySet()) {
            subscribe(entry.getKey(), entry.getValue().getSubscriptionMode());
        }

        persistentMqConsumer.start();
        broadcastMqConsumer.start();

        serviceState = ServiceState.RUNNING;
        logger.info("EventMeshConsumer [{}] started..........", consumerGroup);
    }

    public synchronized void shutdown() throws Exception {
        persistentMqConsumer.shutdown();
        broadcastMqConsumer.shutdown();

        serviceState = ServiceState.STOPED;
        logger.info("EventMeshConsumer [{}] shutdown.........", consumerGroup);
    }

    public ServiceState getStatus() {
        return serviceState;
    }

    public void subscribe(String topic, SubscriptionMode subscriptionMode) throws Exception {
        if (SubscriptionMode.CLUSTERING.equals(subscriptionMode)) {
            persistentMqConsumer.subscribe(topic, createEventListener(subscriptionMode));
        } else if (SubscriptionMode.BROADCASTING.equals(subscriptionMode)) {
            broadcastMqConsumer.subscribe(topic, createEventListener(subscriptionMode));
        } else {
            logger.error("Subscribe Failed. Incorrect Subscription Mode");
            throw new Exception("Subscribe Failed. Incorrect Subscription Mode");
        }
    }

    public void unsubscribe(Subscription.SubscriptionItem subscriptionItem) throws Exception {
        SubscriptionMode mode = subscriptionItem.getMode();
        String topic = subscriptionItem.getTopic();
        if (SubscriptionMode.CLUSTERING.equals(mode)) {
            persistentMqConsumer.unsubscribe(topic);
        } else if (SubscriptionMode.BROADCASTING.equals(mode)) {
            broadcastMqConsumer.unsubscribe(topic);
        } else {
            logger.error("Unsubscribe Failed. Incorrect Subscription Mode");
            throw new Exception("Unsubscribe Failed. Incorrect Subscription Mode");
        }
    }

    public void updateOffset(SubscriptionMode subscriptionMode, List<CloudEvent> events,
                             AbstractContext context) throws Exception {
        if (SubscriptionMode.CLUSTERING.equals(subscriptionMode)) {
            persistentMqConsumer.updateOffset(events, context);
        } else if (SubscriptionMode.BROADCASTING.equals(subscriptionMode)) {
            broadcastMqConsumer.updateOffset(events, context);
        } else {
            logger.error("Subscribe Failed. Incorrect Subscription Mode");
            throw new Exception("Subscribe Failed. Incorrect Subscription Mode");
        }
    }

    private EventListener createEventListener(SubscriptionMode subscriptionMode) {
        return (event, context) -> {

            event = CloudEventBuilder.from(event)
                .withExtension(EventMeshConstants.REQ_MQ2EVENTMESH_TIMESTAMP, String.valueOf(System.currentTimeMillis()))
                .build();

            String topic = event.getSubject();
            Object bizSeqNo = event.getExtension(Constants.PROPERTY_MESSAGE_SEARCH_KEYS);
            String strBizSeqNo = bizSeqNo == null ? "" : bizSeqNo.toString();

            Object uniqueId = event.getExtension(Constants.RMB_UNIQ_ID);
            String strUniqueId = uniqueId == null ? "" : uniqueId.toString();

            if (logger.isDebugEnabled()) {
                logger.debug("message|mq2eventMesh|topic={}|msg={}", topic, event);
            } else {
                logger.info("message|mq2eventMesh|topic={}|bizSeqNo={}|uniqueId={}", topic, bizSeqNo, uniqueId);
            }

            EventMeshAsyncConsumeContext eventMeshAsyncConsumeContext = (EventMeshAsyncConsumeContext) context;

            ConsumerGroupTopicConfig topicConfig = consumerGroupTopicConfig.get(topic);
            if (topicConfig != null) {

                HandleMsgContext handleMsgContext = new HandleMsgContext(EventMeshUtil.buildPushMsgSeqNo(), consumerGroup,
                    this,
                    topic, event, subscriptionMode, eventMeshAsyncConsumeContext.getAbstractContext(), eventMeshGrpcServer, strBizSeqNo, strUniqueId,
                    topicConfig);
                httpMessageHandler.handle(handleMsgContext);
            }
            eventMeshAsyncConsumeContext.commit(EventMeshAction.CommitMessage);
        };
    }
}