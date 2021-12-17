package org.apache.eventmesh.runtime.boot;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.runtime.configuration.EventMeshGrpcConfiguration;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.ConsumerManager;
import org.apache.eventmesh.runtime.core.protocol.grpc.interceptor.MetricsInterceptor;
import org.apache.eventmesh.runtime.core.protocol.grpc.producer.ProducerManager;
import org.apache.eventmesh.runtime.core.protocol.grpc.retry.GrpcRetryer;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.ConsumerService;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.ProducerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

public class EventMeshGrpcServer {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final EventMeshGrpcConfiguration eventMeshGrpcConfiguration;

    private Server server;

    private ProducerManager producerManager;

    private ConsumerManager consumerManager;

    private GrpcRetryer grpcRetryer;

    private ThreadPoolExecutor sendMsgExecutor;

    private ThreadPoolExecutor subscribeMsgExecutor;

    private ThreadPoolExecutor pushMsgExecutor;

    public EventMeshGrpcServer(EventMeshGrpcConfiguration eventMeshGrpcConfiguration) {
        this.eventMeshGrpcConfiguration = eventMeshGrpcConfiguration;
    }

    public void init() throws Exception {
        logger.info("==================EventMeshGRPCServer Initializing==================");

        initThreadPool();

        int serverPort = eventMeshGrpcConfiguration.grpcServerPort;

        producerManager = new ProducerManager(this);
        producerManager.init();

        consumerManager = new ConsumerManager(this);
        consumerManager.init();

        grpcRetryer = new GrpcRetryer(this);
        grpcRetryer.init();

        server = ServerBuilder.forPort(serverPort)
            .intercept(new MetricsInterceptor())
            .addService(new ProducerService(this, sendMsgExecutor))
            .addService(new ConsumerService(this, subscribeMsgExecutor))
            .build();

        logger.info("GRPCServer[port={}] started", serverPort);
        logger.info("-----------------EventMeshGRPCServer initialized");
    }

    public void start() throws Exception {
        logger.info("---------------EventMeshGRPCServer starting-------------------");

        producerManager.start();
        consumerManager.start();
        grpcRetryer.start();
        server.start();

        logger.info("---------------EventMeshGRPCServer running-------------------");
    }

    public void shutdown() throws Exception {
        logger.info("---------------EventMeshGRPCServer stopping-------------------");

        producerManager.shutdown();
        consumerManager.shutdown();
        grpcRetryer.shutdown();

        shutdownThreadPools();

        server.shutdown();

        logger.info("---------------EventMeshGRPCServer stopped-------------------");
    }

    public EventMeshGrpcConfiguration getEventMeshGrpcConfiguration() {
        return this.eventMeshGrpcConfiguration;
    }

    public ProducerManager getProducerManager() {
        return producerManager;
    }

    public ConsumerManager getConsumerManager() {
        return consumerManager;
    }

    public GrpcRetryer getGrpcRetryer() {
        return grpcRetryer;
    }

    public ThreadPoolExecutor getSendMsgExecutor() {
        return sendMsgExecutor;
    }

    public ThreadPoolExecutor getSubscribeMsgExecutor() {
        return subscribeMsgExecutor;
    }

    public ThreadPoolExecutor getPushMsgExecutor() {
        return pushMsgExecutor;
    }

    private void initThreadPool() {
        BlockingQueue<Runnable> sendMsgThreadPoolQueue =
            new LinkedBlockingQueue<Runnable>(eventMeshGrpcConfiguration.eventMeshServerSendMsgBlockQSize);

        sendMsgExecutor = ThreadPoolFactory.createThreadPoolExecutor(eventMeshGrpcConfiguration.eventMeshServerSendMsgThreadNum,
            eventMeshGrpcConfiguration.eventMeshServerSendMsgThreadNum, sendMsgThreadPoolQueue,
            "eventMesh-grpc-sendMsg-", true);

        BlockingQueue<Runnable> subscribeMsgThreadPoolQueue =
            new LinkedBlockingQueue<Runnable>(eventMeshGrpcConfiguration.eventMeshServerSubscribeMsgBlockQSize);

        subscribeMsgExecutor = ThreadPoolFactory.createThreadPoolExecutor(eventMeshGrpcConfiguration.eventMeshServerSubscribeMsgThreadNum,
            eventMeshGrpcConfiguration.eventMeshServerSubscribeMsgThreadNum, subscribeMsgThreadPoolQueue,
            "eventMesh-grpc-subscribeMsg-", true);

        BlockingQueue<Runnable> pushMsgThreadPoolQueue =
            new LinkedBlockingQueue<Runnable>(eventMeshGrpcConfiguration.eventMeshServerPushMsgBlockQSize);
        pushMsgExecutor = ThreadPoolFactory.createThreadPoolExecutor(eventMeshGrpcConfiguration.eventMeshServerPushMsgThreadNum,
                eventMeshGrpcConfiguration.eventMeshServerPushMsgThreadNum, pushMsgThreadPoolQueue,
                "eventMesh-grpc-pushMsg-", true);
    }

    private void shutdownThreadPools() {
        sendMsgExecutor.shutdown();
        subscribeMsgExecutor.shutdown();
        pushMsgExecutor.shutdown();
    }
}