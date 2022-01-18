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

    private final ThreadPoolExecutor threadPoolExecutor;

    public ConsumerService(EventMeshGrpcServer eventMeshGrpcServer,
                           ThreadPoolExecutor threadPoolExecutor) {
        this.eventMeshGrpcServer = eventMeshGrpcServer;
        this.threadPoolExecutor = threadPoolExecutor;
    }

    public void subscribe(Subscription request, StreamObserver<Response> responseObserver) {
        logger.info("cmd={}|{}|client2eventMesh|from={}|to={}",
            "subscribe", EventMeshConstants.PROTOCOL_GRPC,
            request.getHeader().getIp(), eventMeshGrpcServer.getEventMeshGrpcConfiguration().eventMeshIp);

        EventEmitter<Response> emitter = new EventEmitter<>(responseObserver);
        threadPoolExecutor.submit(() -> {
            SubscribeProcessor subscribeProcessor = new SubscribeProcessor(eventMeshGrpcServer);
            try {
                subscribeProcessor.process(request, emitter);
            } catch (Exception e) {
                logger.error("Error code {}, error message {}", StatusCode.EVENTMESH_SUBSCRIBE_ERR.getRetCode(),
                    StatusCode.EVENTMESH_SUBSCRIBE_ERR.getErrMsg(), e);
                ServiceUtils.sendResp(StatusCode.EVENTMESH_SUBSCRIBE_ERR, e.getMessage(), emitter);
            }
        });
    }

    public void subscribeStream(Subscription request, StreamObserver<SimpleMessage> responseObserver) {
        logger.info("cmd={}|{}|client2eventMesh|from={}|to={}",
            "subscribeStream", EventMeshConstants.PROTOCOL_GRPC,
            request.getHeader().getIp(), eventMeshGrpcServer.getEventMeshGrpcConfiguration().eventMeshIp);

        EventEmitter<SimpleMessage> emitter = new EventEmitter<>(responseObserver);
        threadPoolExecutor.submit(() -> {
            SubscribeStreamProcessor streamProcessor = new SubscribeStreamProcessor(eventMeshGrpcServer);
            try {
                streamProcessor.process(request, emitter);
            } catch (Exception e) {
                StatusCode code = StatusCode.EVENTMESH_SUBSCRIBE_ERR;
                logger.error("Error code {}, error message {}", code.getRetCode(), code.getErrMsg(), e);

                Map<String, String> resp = new HashMap<>();
                resp.put("respCode", code.getRetCode());
                resp.put("respMsg", code.getErrMsg() + " " + e.getMessage());

                RequestHeader header = request.getHeader();
                SimpleMessage simpleMessage = SimpleMessage.newBuilder()
                    .setHeader(header)
                    .setContent(JsonUtils.serialize(resp))
                    .build();

                emitter.onNext(simpleMessage);
                emitter.onCompleted();
            }
        });
    }

    public void unsubscribe(Subscription request, StreamObserver<Response> responseObserver) {
        logger.info("cmd={}|{}|client2eventMesh|from={}|to={}",
            "unsubscribe", EventMeshConstants.PROTOCOL_GRPC,
            request.getHeader().getIp(), eventMeshGrpcServer.getEventMeshGrpcConfiguration().eventMeshIp);

        EventEmitter<Response> emitter = new EventEmitter<>(responseObserver);
        threadPoolExecutor.submit(() -> {
            UnsubscribeProcessor unsubscribeProcessor = new UnsubscribeProcessor(eventMeshGrpcServer);
            try {
                unsubscribeProcessor.process(request, emitter);
            } catch (Exception e) {
                logger.error("Error code {}, error message {}", StatusCode.EVENTMESH_UNSUBSCRIBE_ERR.getRetCode(),
                    StatusCode.EVENTMESH_UNSUBSCRIBE_ERR.getErrMsg(), e);
                ServiceUtils.sendResp(StatusCode.EVENTMESH_UNSUBSCRIBE_ERR, e.getMessage(), emitter);
            }
        });
    }
}
