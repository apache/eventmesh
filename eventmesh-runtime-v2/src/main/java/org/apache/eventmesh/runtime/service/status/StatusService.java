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

package org.apache.eventmesh.runtime.service.status;

import org.apache.eventmesh.common.protocol.grpc.adminserver.AdminServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.adminserver.Metadata;
import org.apache.eventmesh.common.protocol.grpc.adminserver.Payload;
import org.apache.eventmesh.common.remote.JobState;
import org.apache.eventmesh.common.remote.request.ReportJobRequest;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;

import java.util.Objects;

import io.grpc.stub.StreamObserver;

import com.google.protobuf.Any;
import com.google.protobuf.UnsafeByteOperations;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StatusService {

    private StreamObserver<Payload> requestObserver;

    private StreamObserver<Payload> responseObserver;

    private AdminServiceGrpc.AdminServiceStub adminServiceStub;

    private AdminServiceGrpc.AdminServiceBlockingStub adminServiceBlockingStub;


    public StatusService(AdminServiceGrpc.AdminServiceStub adminServiceStub, AdminServiceGrpc.AdminServiceBlockingStub adminServiceBlockingStub) {
        this.adminServiceStub = adminServiceStub;
        this.adminServiceBlockingStub = adminServiceBlockingStub;

        responseObserver = new StreamObserver<Payload>() {
            @Override
            public void onNext(Payload response) {
                log.debug("health service receive message: {}|{} ", response.getMetadata(), response.getBody());
            }

            @Override
            public void onError(Throwable t) {
                log.error("health service receive error message: {}", t.getMessage());
            }

            @Override
            public void onCompleted() {
                log.info("health service finished receive message and completed");
            }
        };
        requestObserver = this.adminServiceStub.invokeBiStream(responseObserver);
    }

    public void reportJobStatus(String jobId, JobState jobState) {
        ReportJobRequest reportJobRequest = new ReportJobRequest();
        reportJobRequest.setJobID(jobId);
        reportJobRequest.setState(jobState);
        reportJobRequest.setAddress(IPUtils.getLocalAddress());
        Metadata metadata = Metadata.newBuilder()
                .setType(ReportJobRequest.class.getSimpleName())
                .build();
        Payload payload = Payload.newBuilder()
                .setMetadata(metadata)
                .setBody(Any.newBuilder().setValue(UnsafeByteOperations.unsafeWrap(Objects.requireNonNull(JsonUtils.toJSONBytes(reportJobRequest))))
                        .build())
                .build();
        log.info("report job state request: {}", JsonUtils.toJSONString(reportJobRequest));
        requestObserver.onNext(payload);
    }

    public void stop() {
        if (requestObserver != null) {
            requestObserver.onCompleted();
        }
    }
}
