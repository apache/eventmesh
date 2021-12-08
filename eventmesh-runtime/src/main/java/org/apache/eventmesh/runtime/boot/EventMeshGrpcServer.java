package org.apache.eventmesh.runtime.boot;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.PublisherServiceImpl;

public class EventMeshGrpcServer {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private Server server;

    public EventMeshGrpcServer() {

    }

    public void init() throws Exception {
        logger.info("==================EventMeshGRPCServer Initializing==================");

        server = ServerBuilder.forPort(5005).addService(new PublisherServiceImpl()).build();

        logger.info("GRPCServer[port=5005] started");
        logger.info("-----------------EventMeshGRPCServer initialized");
    }

    public void start() throws Exception {
        logger.info("---------------EventMeshGRPCServer starting-------------------");

        server.start();

        logger.info("---------------EventMeshGRPCServer running-------------------");
    }

    public void shutdown() throws Exception {
        logger.info("---------------EventMeshGRPCServer stopping-------------------");

        server.shutdown();

        logger.info("---------------EventMeshGRPCServer stopped-------------------");
    }
}
