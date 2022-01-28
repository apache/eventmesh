package org.apache.eventmesh.runtime.core.protocol.grpc.service;

import io.grpc.stub.StreamObserver;
import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;
import org.apache.eventmesh.common.protocol.grpc.protos.ConsumerServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.protos.RequestHeader;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.apache.eventmesh.common.protocol.grpc.protos.SimpleMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.grpc.processor.ReplyMessageProcessor;
import org.apache.eventmesh.runtime.core.protocol.grpc.processor.SubscribeProcessor;
import org.apache.eventmesh.runtime.core.protocol.grpc.processor.SubscribeStreamProcessor;
import org.apache.eventmesh.runtime.core.protocol.grpc.processor.UnsubscribeProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

public class ConsumerService extends ConsumerServiceGrpc.ConsumerServiceImplBase {

    private final Logger logger = LoggerFactory.getLogger(ProducerService.class);

    private final EventMeshGrpcServer eventMeshGrpcServer;

    private final ThreadPoolExecutor subscribeThreadPoolExecutor;

    private final ThreadPoolExecutor replyThreadPoolExecutor;

    public ConsumerService(EventMeshGrpcServer eventMeshGrpcServer,
                           ThreadPoolExecutor subscribeThreadPoolExecutor,
                           ThreadPoolExecutor replyThreadPoolExecutor) {
        this.eventMeshGrpcServer = eventMeshGrpcServer;
        this.subscribeThreadPoolExecutor = subscribeThreadPoolExecutor;
        this.replyThreadPoolExecutor = replyThreadPoolExecutor;
    }

    public void subscribe(Subscription request, StreamObserver<Response> responseObserver) {
        logger.info("cmd={}|{}|client2eventMesh|from={}|to={}",
            "subscribe", EventMeshConstants.PROTOCOL_GRPC,
            request.getHeader().getIp(), eventMeshGrpcServer.getEventMeshGrpcConfiguration().eventMeshIp);

        EventEmitter<Response> emitter = new EventEmitter<>(responseObserver);
        subscribeThreadPoolExecutor.submit(() -> {
            SubscribeProcessor subscribeProcessor = new SubscribeProcessor(eventMeshGrpcServer);
            try {
                subscribeProcessor.process(request, emitter);
            } catch (Exception e) {
                logger.error("Error code {}, error message {}", StatusCode.EVENTMESH_SUBSCRIBE_ERR.getRetCode(),
                    StatusCode.EVENTMESH_SUBSCRIBE_ERR.getErrMsg(), e);
                ServiceUtils.sendRespAndDone(StatusCode.EVENTMESH_SUBSCRIBE_ERR, e.getMessage(), emitter);
            }
        });
    }

    public StreamObserver<Subscription> subscribeStream(StreamObserver<SimpleMessage> responseObserver) {
        EventEmitter<SimpleMessage> emitter = new EventEmitter<>(responseObserver);

        return new StreamObserver<Subscription>() {
            @Override
            public void onNext(Subscription subscription) {
                if (subscription.getReply() == null) {
                    logger.info("cmd={}|{}|client2eventMesh|from={}|to={}",
                        "subscribeStream", EventMeshConstants.PROTOCOL_GRPC,
                        subscription.getHeader().getIp(), eventMeshGrpcServer.getEventMeshGrpcConfiguration().eventMeshIp);

                    handleSubscriptionStream(subscription, emitter);
                } else {
                    logger.info("cmd={}|{}|client2eventMesh|from={}|to={}",
                        "reply-to-server", EventMeshConstants.PROTOCOL_GRPC,
                        subscription.getHeader().getIp(), eventMeshGrpcServer.getEventMeshGrpcConfiguration().eventMeshIp);

                    handleSubscribeReply(subscription, emitter);
                }
            }

            @Override
            public void onError(Throwable t) {
                responseObserver.onCompleted();
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }

    private void handleSubscriptionStream(Subscription request, EventEmitter<SimpleMessage> emitter) {
        subscribeThreadPoolExecutor.submit(() -> {
            SubscribeStreamProcessor streamProcessor = new SubscribeStreamProcessor(eventMeshGrpcServer);
            try {
                streamProcessor.process(request, emitter);
            } catch (Exception e) {
                logger.error("Error code {}, error message {}", StatusCode.EVENTMESH_SUBSCRIBE_ERR, e.getMessage(), e);
                ServiceUtils.sendStreamRespAndDone(request.getHeader(), StatusCode.EVENTMESH_SUBSCRIBE_ERR, e.getMessage(), emitter);
            }
        });
    }

    private void handleSubscribeReply(Subscription subscription, EventEmitter<SimpleMessage> emitter) {
        replyThreadPoolExecutor.submit(() -> {
            ReplyMessageProcessor replyMessageProcessor = new ReplyMessageProcessor(eventMeshGrpcServer);
            try {
                replyMessageProcessor.process(buildSimpleMessage(subscription), emitter);
            } catch (Exception e) {
                logger.error("Error code {}, error message {}", StatusCode.EVENTMESH_SUBSCRIBE_ERR, e.getMessage(), e);
                ServiceUtils.sendStreamRespAndDone(subscription.getHeader(), StatusCode.EVENTMESH_SUBSCRIBE_ERR, e.getMessage(), emitter);
            }
        });
    }

    private SimpleMessage buildSimpleMessage(Subscription subscription) {
        Subscription.Reply reply = subscription.getReply();
        return SimpleMessage.newBuilder()
            .setHeader(subscription.getHeader())
            .setProducerGroup(reply.getProducerGroup())
            .setContent(reply.getContent())
            .setUniqueId(reply.getUniqueId())
            .setSeqNum(reply.getSeqNum())
            .setTopic(reply.getTopic())
            .setTtl(reply.getTtl())
            .putAllProperties(reply.getPropertiesMap())
            .build();
    }

    public void unsubscribe(Subscription request, StreamObserver<Response> responseObserver) {
        logger.info("cmd={}|{}|client2eventMesh|from={}|to={}",
            "unsubscribe", EventMeshConstants.PROTOCOL_GRPC,
            request.getHeader().getIp(), eventMeshGrpcServer.getEventMeshGrpcConfiguration().eventMeshIp);

        EventEmitter<Response> emitter = new EventEmitter<>(responseObserver);
        subscribeThreadPoolExecutor.submit(() -> {
            UnsubscribeProcessor unsubscribeProcessor = new UnsubscribeProcessor(eventMeshGrpcServer);
            try {
                unsubscribeProcessor.process(request, emitter);
            } catch (Exception e) {
                logger.error("Error code {}, error message {}", StatusCode.EVENTMESH_UNSUBSCRIBE_ERR.getRetCode(),
                    StatusCode.EVENTMESH_UNSUBSCRIBE_ERR.getErrMsg(), e);
                ServiceUtils.sendRespAndDone(StatusCode.EVENTMESH_UNSUBSCRIBE_ERR, e.getMessage(), emitter);
            }
        });
    }
}
