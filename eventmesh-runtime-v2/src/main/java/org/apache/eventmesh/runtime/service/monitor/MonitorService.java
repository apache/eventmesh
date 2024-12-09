/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.runtime.service.monitor;

import org.apache.eventmesh.common.protocol.grpc.adminserver.AdminServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.adminserver.Metadata;
import org.apache.eventmesh.common.protocol.grpc.adminserver.Payload;
import org.apache.eventmesh.common.remote.request.ReportMonitorRequest;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.openconnect.api.monitor.Monitor;
import org.apache.eventmesh.openconnect.api.monitor.MonitorRegistry;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.grpc.stub.StreamObserver;

import com.google.protobuf.Any;
import com.google.protobuf.UnsafeByteOperations;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MonitorService {

    private final ScheduledExecutorService scheduler;

    private StreamObserver<Payload> requestObserver;

    private StreamObserver<Payload> responseObserver;

    private AdminServiceGrpc.AdminServiceStub adminServiceStub;

    private AdminServiceGrpc.AdminServiceBlockingStub adminServiceBlockingStub;


    public MonitorService(AdminServiceGrpc.AdminServiceStub adminServiceStub, AdminServiceGrpc.AdminServiceBlockingStub adminServiceBlockingStub) {
        this.adminServiceStub = adminServiceStub;
        this.adminServiceBlockingStub = adminServiceBlockingStub;

        this.scheduler = Executors.newSingleThreadScheduledExecutor();

        responseObserver = new StreamObserver<Payload>() {
            @Override
            public void onNext(Payload response) {
                log.debug("monitor service receive message: {}|{} ", response.getMetadata(), response.getBody());
            }

            @Override
            public void onError(Throwable t) {
                log.error("monitor service receive error message: {}", t.getMessage());
            }

            @Override
            public void onCompleted() {
                log.info("monitor service finished receive message and completed");
            }
        };
        requestObserver = this.adminServiceStub.invokeBiStream(responseObserver);
    }

    public void registerMonitor(Monitor monitor) {
        MonitorRegistry.registerMonitor(monitor);
    }

    public void start() {
        this.startReporting();
    }

    public void startReporting() {
        scheduler.scheduleAtFixedRate(() -> {
            List<Monitor> monitors = MonitorRegistry.getMonitors();
            for (Monitor monitor : monitors) {
                monitor.printMetrics();
                reportToAdminService(monitor);
            }
        }, 5, 30, TimeUnit.SECONDS);
    }

    private void reportToAdminService(Monitor monitor) {
        ReportMonitorRequest request = new ReportMonitorRequest();
        if (monitor instanceof SourceMonitor) {
            SourceMonitor sourceMonitor = (SourceMonitor) monitor;
            request.setTaskID(sourceMonitor.getTaskId());
            request.setJobID(sourceMonitor.getJobId());
            request.setAddress(sourceMonitor.getIp());
            request.setConnectorStage(sourceMonitor.getConnectorStage());
            request.setTotalReqNum(sourceMonitor.getTotalRecordNum().longValue());
            request.setTotalTimeCost(sourceMonitor.getTotalTimeCost().longValue());
            request.setMaxTimeCost(sourceMonitor.getMaxTimeCost().longValue());
            request.setAvgTimeCost(sourceMonitor.getAverageTime());
            request.setTps(sourceMonitor.getTps());
        } else if (monitor instanceof SinkMonitor) {
            SinkMonitor sinkMonitor = (SinkMonitor) monitor;
            request.setTaskID(sinkMonitor.getTaskId());
            request.setJobID(sinkMonitor.getJobId());
            request.setAddress(sinkMonitor.getIp());
            request.setConnectorStage(sinkMonitor.getConnectorStage());
            request.setTotalReqNum(sinkMonitor.getTotalRecordNum().longValue());
            request.setTotalTimeCost(sinkMonitor.getTotalTimeCost().longValue());
            request.setMaxTimeCost(sinkMonitor.getMaxTimeCost().longValue());
            request.setAvgTimeCost(sinkMonitor.getAverageTime());
            request.setTps(sinkMonitor.getTps());
        } else {
            throw new IllegalArgumentException("Unsupported monitor: " + monitor);
        }

        Metadata metadata = Metadata.newBuilder()
            .setType(ReportMonitorRequest.class.getSimpleName())
            .build();
        Payload payload = Payload.newBuilder()
            .setMetadata(metadata)
            .setBody(Any.newBuilder().setValue(UnsafeByteOperations.unsafeWrap(Objects.requireNonNull(JsonUtils.toJSONBytes(request))))
                .build())
            .build();
        requestObserver.onNext(payload);
    }

    public void stop() {
        scheduler.shutdown();
        if (requestObserver != null) {
            requestObserver.onCompleted();
        }
    }

}
