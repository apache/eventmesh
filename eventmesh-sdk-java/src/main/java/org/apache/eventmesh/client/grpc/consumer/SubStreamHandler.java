package org.apache.eventmesh.client.grpc.consumer;

import io.grpc.stub.StreamObserver;
import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.client.grpc.util.EventMeshClientUtil;
import org.apache.eventmesh.common.protocol.grpc.protos.ConsumerServiceGrpc.ConsumerServiceStub;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.apache.eventmesh.common.protocol.grpc.protos.SimpleMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription;
import java.util.Optional;

public class SubStreamHandler<T> {

    private ConsumerServiceStub consumerAsyncClient;

    private EventMeshGrpcClientConfig clientConfig;

    private StreamObserver<Subscription> sender;

    private ReceiveMsgHook<T> listener;

    public SubStreamHandler(ConsumerServiceStub consumerAsyncClient, EventMeshGrpcClientConfig clientConfig,
                            ReceiveMsgHook<T> listener) {
        this.consumerAsyncClient = consumerAsyncClient;
        this.clientConfig = clientConfig;
        this.listener = listener;
    }

    public void sendSubscription(Subscription subscription) {
        synchronized (this) {
            if (this.sender == null) {
                this.sender = consumerAsyncClient.subscribeStream(createReceiver());
            }
        }
       this.sender.onNext(subscription);
    }

    private StreamObserver<SimpleMessage> createReceiver() {
        return new StreamObserver<SimpleMessage>() {
            @Override
            public void onNext(SimpleMessage message) {
                T msg = EventMeshClientUtil.buildMessage(message, listener.getProtocolType());
                if (!(msg instanceof Response)) {
                    Optional<T> reply = listener.handle(msg);
                    if (reply.isPresent()) {
                        Subscription streamReply = buildReplyMessage(reply.get());
                        sender.onNext(streamReply);
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                sender.onCompleted();
            }

            @Override
            public void onCompleted() {
                sender.onCompleted();
            }
        };
    }

    private Subscription buildReplyMessage(T message) {
        SimpleMessage simpleMessage = EventMeshClientUtil.buildSimpleMessage(message, clientConfig, listener.getProtocolType());

        // set the producerGroup
        simpleMessage = SimpleMessage.newBuilder(simpleMessage)
            .setProducerGroup(clientConfig.getConsumerGroup())
            .build();

        Subscription.Reply reply = Subscription.Reply.newBuilder()
            .setProducerGroup(simpleMessage.getProducerGroup())
            .setTopic(simpleMessage.getTopic())
            .setContent(simpleMessage.getContent())
            .setSeqNum(simpleMessage.getSeqNum())
            .setUniqueId(simpleMessage.getUniqueId())
            .setTtl(simpleMessage.getTtl())
            .putAllProperties(simpleMessage.getPropertiesMap())
            .build();

        return Subscription.newBuilder()
            .setHeader(simpleMessage.getHeader())
            .setReply(reply).build();
    }

    public void close() {
        if (this.sender != null) {
            this.sender.onCompleted();
        }
    }
}
