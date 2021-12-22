package org.apache.eventmesh.runtime.core.protocol.grpc.consumer;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.EventMeshAction;
import org.apache.eventmesh.api.EventMeshAsyncConsumeContext;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription.SubscriptionItem.SubscriptionMode;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.common.ServiceState;
import org.apache.eventmesh.runtime.configuration.EventMeshGrpcConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.plugin.MQConsumerWrapper;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.ConsumerGroupClient;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.ConsumerGroupTopicConfig;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.GrpcType;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.StreamTopicConfig;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.WebhookTopicConfig;
import org.apache.eventmesh.runtime.core.protocol.grpc.producer.EventMeshProducer;
import org.apache.eventmesh.runtime.core.protocol.grpc.producer.SendMessageContext;
import org.apache.eventmesh.runtime.core.protocol.grpc.push.HandleMsgContext;
import org.apache.eventmesh.runtime.core.protocol.grpc.push.MessageHandler;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class EventMeshConsumer {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final String consumerGroup;

    private final EventMeshGrpcServer eventMeshGrpcServer;

    private final EventMeshGrpcConfiguration eventMeshGrpcConfiguration;

    private final MQConsumerWrapper persistentMqConsumer;

    private final MQConsumerWrapper broadcastMqConsumer;

    private final MessageHandler messageHandler;

    private ServiceState serviceState;

    /**
     * Key: topic
     * Value: ConsumerGroupTopicConfig
     **/
    private final Map<String, ConsumerGroupTopicConfig> consumerGroupTopicConfig = new ConcurrentHashMap<>();

    public EventMeshConsumer(EventMeshGrpcServer eventMeshGrpcServer, String consumerGroup) {
        this.eventMeshGrpcServer = eventMeshGrpcServer;
        this.eventMeshGrpcConfiguration = eventMeshGrpcServer.getEventMeshGrpcConfiguration();
        this.consumerGroup = consumerGroup;
        this.messageHandler = new MessageHandler(consumerGroup, eventMeshGrpcServer.getPushMsgExecutor());
        this.persistentMqConsumer = new MQConsumerWrapper(eventMeshGrpcConfiguration.eventMeshConnectorPluginType);
        this.broadcastMqConsumer = new MQConsumerWrapper(eventMeshGrpcConfiguration.eventMeshConnectorPluginType);
    }

    /**
     * Register client's topic information and return true if this EventMeshConsumer required restart because of the topic changes
     * @param client ConsumerGroupClient
     * @return true if the underlining EventMeshConsumer needs to restart later; false otherwise
     */
    public synchronized boolean registerClient(ConsumerGroupClient client) {
        boolean requireRestart = false;
        GrpcType grpcType = client.getGrpcType();
        String topic = client.getTopic();
        SubscriptionMode subscriptionMode = client.getSubscriptionMode();

        ConsumerGroupTopicConfig topicConfig = consumerGroupTopicConfig.get(topic);
        if (topicConfig == null) {
            topicConfig = ConsumerGroupTopicConfig.buildTopicConfig(consumerGroup, topic, subscriptionMode, grpcType);
            consumerGroupTopicConfig.put(topic, topicConfig);
            requireRestart = true;
        }
        topicConfig.registerClient(client);
        return requireRestart;
    }

    /**
     * Deregister client's topic information and return true if this EventMeshConsumer required restart because of the topic changes
     * @param client ConsumerGroupClient
     * @return true if the underlining EventMeshConsumer needs to restart later; false otherwise
     */
    public synchronized boolean deregisterClient(ConsumerGroupClient client) {
        boolean requireRestart = false;
        String topic = client.getTopic();
        ConsumerGroupTopicConfig topicConfig = consumerGroupTopicConfig.get(topic);
        if (topicConfig != null) {
            topicConfig.deregisterClient(client);
            if (topicConfig.getSize() == 0) {
                consumerGroupTopicConfig.remove(topic);
                requireRestart = true;
            }
        }
        return requireRestart;
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
        if (consumerGroupTopicConfig.size() == 0) {
            // no topics, don't start the consumer
            return;
        }

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
                GrpcType grpcType = topicConfig.getGrpcType();
                HandleMsgContext handleMsgContext = new HandleMsgContext(consumerGroup, event, subscriptionMode, grpcType,
                    eventMeshAsyncConsumeContext.getAbstractContext(), eventMeshGrpcServer, this, topicConfig);

                if (messageHandler.handle(handleMsgContext)) {
                    eventMeshAsyncConsumeContext.commit(EventMeshAction.ManualAck);
                    return;
                } else {
                    // can not handle the message due to the capacity limit is reached
                    // wait for sometime and send this message back to mq and consume again
                    try {
                        Thread.sleep(5000);
                        sendMessageBack(consumerGroup, event, strUniqueId, strBizSeqNo);
                    } catch (Exception ignored) {
                        // ignore exception
                    }
                }
            }
            eventMeshAsyncConsumeContext.commit(EventMeshAction.CommitMessage);
        };
    }

    public void sendMessageBack(String consumerGroup, final CloudEvent event, final String uniqueId, String bizSeqNo) throws Exception {
        EventMeshProducer producer
            = eventMeshGrpcServer.getProducerManager().getEventMeshProducer(consumerGroup);

        if (producer == null) {
            logger.warn("consumer:{} consume fail, sendMessageBack, bizSeqNo:{}, uniqueId:{}", consumerGroup, bizSeqNo, uniqueId);
            return;
        }

        final SendMessageContext sendMessageBackContext = new SendMessageContext(bizSeqNo, event, producer, eventMeshGrpcServer);

        producer.send(sendMessageBackContext, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
            }

            @Override
            public void onException(OnExceptionContext context) {
                logger.warn("consumer:{} consume fail, sendMessageBack, bizSeqNo:{}, uniqueId:{}", consumerGroup, bizSeqNo, uniqueId);
            }
        });
    }
}