package org.apache.eventmesh.runtime.boot;

import org.apache.eventmesh.common.config.ConfigService;
import org.apache.eventmesh.runtime.rpc.AdminBiStreamServiceGrpc;
import org.apache.eventmesh.runtime.rpc.AdminBiStreamServiceGrpc.AdminBiStreamServiceStub;
import org.apache.eventmesh.runtime.rpc.Payload;
import org.apache.eventmesh.runtime.util.BannerUtil;

import java.io.File;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import com.google.protobuf.Any;
import com.google.protobuf.StringValue;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RuntimeInstanceStarter {

    public static void main(String[] args) {
        // TODO:加载配置,从环境变量中拿到JobID,并去Admin获取Job配置
        // TODO:启动grpc server,连接META获取Admin地址,上报心跳
        // TODO:添加shutDownHook

        try {
            EventMeshServer server = new EventMeshServer();
            BannerUtil.generateBanner();
            server.init();
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    log.info("runtime shutting down hook begin.");
                    long start = System.currentTimeMillis();
                    server.shutdown();
                    long end = System.currentTimeMillis();

                    log.info("runtime shutdown cost {}ms", end - start);
                } catch (Exception e) {
                    log.error("exception when shutdown.", e);
                }
            }));
        } catch (Throwable e) {
            log.error("EventMesh start fail.", e);
            System.exit(-1);
        }

        // 创建gRPC通道
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 50051)
            .usePlaintext()
            .build();

        // 创建gRPC客户端存根
        AdminBiStreamServiceStub stub = AdminBiStreamServiceGrpc.newStub(channel);

        // 创建一个响应观察者
        StreamObserver<Payload> responseObserver = new StreamObserver<Payload>() {
            @Override
            public void onNext(Payload response) {
                log.info("runtime receive message: {} ", response);
            }

            @Override
            public void onError(Throwable t) {
                log.error("runtime receive error message: {}", t.getMessage());
            }

            @Override
            public void onCompleted() {
                log.info("runtime finished receive message and completed");
            }
        };

        // 创建一个请求观察者
        StreamObserver<Payload> requestObserver = stub.invokeBiStream(responseObserver);
        StringValue stringValue = StringValue.newBuilder().setValue("test").build();
        Any test = Any.pack(stringValue);
        // 发送请求
        for (int i = 0; i < 10; i++) {
            Payload request = Payload.newBuilder()
                .setBody(test)
                .build();
            requestObserver.onNext(request);
        }

        // 完成请求流
        requestObserver.onCompleted();

        // 等待响应
        Thread.sleep(10000);

        // 关闭通道
        channel.shutdown();

    }
}
