package org.apache.eventmesh.client.grpc.producer;

import io.cloudevents.CloudEvent;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.client.grpc.util.EventMeshClientUtil;
import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.BatchMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.PublisherServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.protos.PublisherServiceGrpc.PublisherServiceBlockingStub;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.apache.eventmesh.common.protocol.grpc.protos.SimpleMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class EventMeshGrpcProducer implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(EventMeshGrpcProducer.class);

    private final static String PROTOCOL_TYPE = EventMeshCommon.EM_MESSAGE_PROTOCOL_NAME;

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

    public CloudEvent requestReply(CloudEvent cloudEvent, int timeout) {
        return cloudEventProducer.requestReply(cloudEvent, timeout);
    }

    public Response publish(EventMeshMessage message) {
        logger.info("Publish message " + message.toString());

        SimpleMessage simpleMessage = EventMeshClientUtil.buildSimpleMessage(message, clientConfig, PROTOCOL_TYPE);
        try {
            Response response = publisherClient.publish(simpleMessage);
            logger.info("Received response " + response.toString());
            return response;
        } catch (Exception e) {
            logger.error("Error in publishing message {}, error {}", message, e.getMessage());
            return null;
        }
    }

    public EventMeshMessage requestReply(EventMeshMessage message, int timeout) {
        logger.info("RequestReply message " + message.toString());

        SimpleMessage simpleMessage = EventMeshClientUtil.buildSimpleMessage(message, clientConfig, PROTOCOL_TYPE);
        try {
            SimpleMessage reply = publisherClient.withDeadlineAfter(timeout, TimeUnit.MILLISECONDS).requestReply(simpleMessage);
            logger.info("Received reply message" + reply.toString());

            Object msg = EventMeshClientUtil.buildMessage(reply, PROTOCOL_TYPE);
            if (msg instanceof EventMeshMessage) {
                return (EventMeshMessage) msg;
            } else {
                return null;
            }
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
        BatchMessage batchMessage = EventMeshClientUtil.buildBatchMessages(messageList, clientConfig, PROTOCOL_TYPE);
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
}