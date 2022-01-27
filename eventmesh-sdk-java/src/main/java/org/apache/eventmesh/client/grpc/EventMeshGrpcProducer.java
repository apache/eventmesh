package org.apache.eventmesh.client.grpc;

import io.cloudevents.CloudEvent;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.client.grpc.producer.CloudEventProducer;
import org.apache.eventmesh.client.grpc.util.EventMeshClientUtil;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.BatchMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.PublisherServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.protos.PublisherServiceGrpc.PublisherServiceBlockingStub;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.apache.eventmesh.common.protocol.grpc.protos.SimpleMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class EventMeshGrpcProducer implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(EventMeshGrpcProducer.class);

    private final static String PROTOCOL_TYPE = "eventmeshmessage";

    private final EventMeshGrpcClientConfig clientConfig;

    private ManagedChannel channel;

    private PublisherServiceBlockingStub publisherClient;

    private CloudEventProducer cloudEventProducer;

    public EventMeshGrpcProducer(EventMeshGrpcClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    public void init() {
        channel = ManagedChannelBuilder.forAddress(clientConfig.getServerAddr(), clientConfig.getServerPort())
                .usePlaintext().build();
        publisherClient = PublisherServiceGrpc.newBlockingStub(channel);

        cloudEventProducer = new CloudEventProducer(clientConfig, publisherClient);
    }

    public Response publish(CloudEvent cloudEvent) {
        return cloudEventProducer.publish(cloudEvent);
    }

    public Response requestReply(CloudEvent cloudEvent, int timeout) {
        return cloudEventProducer.requestReply(cloudEvent, timeout);
    }

    public Response publish(EventMeshMessage message) {
        logger.info("Publish message " + message.toString());

        SimpleMessage simpleMessage = buildMessage(message);
        try {
            Response response = publisherClient.publish(simpleMessage);
            logger.info("Received response " + response.toString());
            return response;
        } catch (Exception e) {
            logger.error("Error in publishing message {}, error {}", message, e.getMessage());
            return null;
        }
    }

    public Response requestReply(EventMeshMessage message, int timeout) {
        logger.info("RequestReply message " + message.toString());

        SimpleMessage simpleMessage = buildMessage(message);
        try {
            Response response = publisherClient.requestReply(simpleMessage);
            logger.info("Received response " + response.toString());
            return response;
        } catch (Exception e) {
            logger.error("Error in RequestReply message {}, error {}", message, e.getMessage());
            return null;
        }
    }

    public <T> Response publish(List<T> messageList) {
        logger.info("BatchPublish message " + messageList.toString());

        if (messageList.size() == 0) {
            return null;
        }
        if (messageList.get(0) instanceof CloudEvent) {
            return cloudEventProducer.publish((List<CloudEvent>) messageList);
        }
        BatchMessage batchMessage = buildBatchMessages((List<EventMeshMessage>) messageList);
        try {
            Response response = publisherClient.batchPublish(batchMessage);
            logger.info("Received response " + response.toString());
            return response;
        } catch (Exception e) {
            logger.error("Error in BatchPublish message {}, error {}", messageList, e.getMessage());
            return null;
        }
    }

    public void close() {
        channel.shutdown();
    }

    private SimpleMessage buildMessage(EventMeshMessage message) {
        return SimpleMessage.newBuilder()
            .setHeader(EventMeshClientUtil.buildHeader(clientConfig, PROTOCOL_TYPE))
            .setProducerGroup(clientConfig.getProducerGroup())
            .setTopic(message.getTopic())
            .setContent(message.getContent())
            .setSeqNum(message.getBizSeqNo())
            .setUniqueId(message.getUniqueId())
            .setTtl(message.getProp(Constants.EVENTMESH_MESSAGE_CONST_TTL))
            .putAllProperties(message.getProp())
            .build();
    }

    private BatchMessage buildBatchMessages(List<EventMeshMessage> messageList) {
        BatchMessage.Builder messageBuilder = BatchMessage.newBuilder()
            .setHeader(EventMeshClientUtil.buildHeader(clientConfig, PROTOCOL_TYPE))
            .setProducerGroup(clientConfig.getProducerGroup());

        for (EventMeshMessage message: messageList) {
            messageBuilder.setTopic(message.getTopic());
            BatchMessage.MessageItem item = BatchMessage.MessageItem.newBuilder()
                .setContent(message.getContent())
                .setUniqueId(message.getUniqueId())
                .setSeqNum(message.getBizSeqNo())
                .setTtl(message.getProp(Constants.EVENTMESH_MESSAGE_CONST_TTL))
                .putAllProperties(message.getProp())
                .build();
            messageBuilder.addMessageItem(item);
        }
        return messageBuilder.build();
    }
}
