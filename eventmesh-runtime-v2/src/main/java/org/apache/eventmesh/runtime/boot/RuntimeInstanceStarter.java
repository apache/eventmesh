package org.apache.eventmesh.runtime.boot;

import org.apache.eventmesh.common.config.ConfigService;
import org.apache.eventmesh.runtime.RuntimeInstanceConfig;
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
            RuntimeInstanceConfig runtimeInstanceConfig = ConfigService.getInstance().buildConfigInstance(RuntimeInstanceConfig.class);
            RuntimeInstance runtimeInstance = new RuntimeInstance(runtimeInstanceConfig);
            BannerUtil.generateBanner();
            runtimeInstance.init();
            runtimeInstance.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    log.info("runtime shutting down hook begin.");
                    long start = System.currentTimeMillis();
                    runtimeInstance.shutdown();
                    long end = System.currentTimeMillis();

                    log.info("runtime shutdown cost {}ms", end - start);
                } catch (Exception e) {
                    log.error("exception when shutdown.", e);
                }
            }));
        } catch (Throwable e) {
            log.error("runtime start fail.", e);
            System.exit(-1);
        }

    }
}
