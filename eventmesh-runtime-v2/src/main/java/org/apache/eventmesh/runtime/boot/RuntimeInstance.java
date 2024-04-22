package org.apache.eventmesh.runtime.boot;

import org.apache.eventmesh.runtime.Runtime;
import org.apache.eventmesh.runtime.RuntimeFactory;
import org.apache.eventmesh.runtime.RuntimeInstanceConfig;
import org.apache.eventmesh.runtime.meta.MetaStorage;
import org.apache.eventmesh.runtime.rpc.AdminBiStreamServiceGrpc;
import org.apache.eventmesh.runtime.rpc.AdminBiStreamServiceGrpc.AdminBiStreamServiceStub;
import org.apache.eventmesh.runtime.rpc.Payload;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import com.google.protobuf.Any;
import com.google.protobuf.StringValue;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RuntimeInstance {

    private String adminServerAddr = "localhost";

    private int adminServerPort = 50051;

    private ManagedChannel channel;

    private AdminBiStreamServiceStub adminStub;

    StreamObserver<Payload> responseObserver;

    StreamObserver<Payload> requestObserver;

    private MetaStorage metaStorage;

    private Runtime runtime;

    private RuntimeFactory runtimeFactory;

    private RuntimeInstanceConfig runtimeInstanceConfig;

    public RuntimeInstance(RuntimeInstanceConfig runtimeInstanceConfig) {
        this.runtimeInstanceConfig = runtimeInstanceConfig;
        this.metaStorage = MetaStorage.getInstance(runtimeInstanceConfig.getRegistryPluginType());
    }

    public void init () {
        metaStorage.init();

    }

    public void start() {
        metaStorage.start();

        // TODO：从registry发现admin server地址列表

        // 创建gRPC通道
        channel = ManagedChannelBuilder.forAddress(adminServerAddr, adminServerPort)
            .usePlaintext()
            .build();

        adminStub = AdminBiStreamServiceGrpc.newStub(channel);

        responseObserver = new StreamObserver<Payload>() {
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

        requestObserver = adminStub.invokeBiStream(responseObserver);
        StringValue stringValue = StringValue.newBuilder().setValue("test").build();
        Any test = Any.pack(stringValue);
        // 发送请求
        for (int i = 0; i < 10; i++) {
            Payload request = Payload.newBuilder()
                .setBody(test)
                .build();
            requestObserver.onNext(request);
        }

    }

    public void shutdown(){
        requestObserver.onCompleted();
        channel.shutdown();
    }

}
