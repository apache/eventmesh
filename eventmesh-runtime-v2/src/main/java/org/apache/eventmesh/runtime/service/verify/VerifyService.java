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

package org.apache.eventmesh.runtime.service.verify;

import org.apache.eventmesh.common.enums.ConnectorStage;
import org.apache.eventmesh.common.protocol.grpc.adminserver.AdminServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.adminserver.Metadata;
import org.apache.eventmesh.common.protocol.grpc.adminserver.Payload;
import org.apache.eventmesh.common.remote.request.ReportVerifyRequest;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.runtime.connector.ConnectorRuntimeConfig;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.grpc.stub.StreamObserver;

import com.google.protobuf.Any;
import com.google.protobuf.UnsafeByteOperations;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VerifyService {

    private final ExecutorService reportVerifyExecutor;

    private StreamObserver<Payload> requestObserver;

    private StreamObserver<Payload> responseObserver;

    private AdminServiceGrpc.AdminServiceStub adminServiceStub;

    private AdminServiceGrpc.AdminServiceBlockingStub adminServiceBlockingStub;

    private ConnectorRuntimeConfig connectorRuntimeConfig;


    public VerifyService(AdminServiceGrpc.AdminServiceStub adminServiceStub, AdminServiceGrpc.AdminServiceBlockingStub adminServiceBlockingStub,
                         ConnectorRuntimeConfig connectorRuntimeConfig) {
        this.adminServiceStub = adminServiceStub;
        this.adminServiceBlockingStub = adminServiceBlockingStub;
        this.connectorRuntimeConfig = connectorRuntimeConfig;

        this.reportVerifyExecutor = Executors.newSingleThreadExecutor();

        responseObserver = new StreamObserver<Payload>() {
            @Override
            public void onNext(Payload response) {
                log.debug("verify service receive message: {}|{} ", response.getMetadata(), response.getBody());
            }

            @Override
            public void onError(Throwable t) {
                log.error("verify service receive error message: {}", t.getMessage());
            }

            @Override
            public void onCompleted() {
                log.info("verify service finished receive message and completed");
            }
        };
        requestObserver = this.adminServiceStub.invokeBiStream(responseObserver);
    }

    public void reportVerifyRequest(ConnectRecord record, ConnectorStage connectorStage) {
        reportVerifyExecutor.submit(() -> {
            try {
                byte[] data = (byte[])record.getData();
                // use record data + recordUniqueId for md5
                String md5Str = md5(Arrays.toString(data) + record.getExtension("recordUniqueId"));
                ReportVerifyRequest reportVerifyRequest = new ReportVerifyRequest();
                reportVerifyRequest.setTaskID(connectorRuntimeConfig.getTaskID());
                reportVerifyRequest.setJobID(connectorRuntimeConfig.getJobID());
                reportVerifyRequest.setRecordID(record.getExtension("recordUniqueId"));
                reportVerifyRequest.setRecordSig(md5Str);
                reportVerifyRequest.setConnectorName(
                        IPUtils.getLocalAddress() + "_" + connectorRuntimeConfig.getJobID() + "_" + connectorRuntimeConfig.getRegion());
                reportVerifyRequest.setConnectorStage(connectorStage.name());
                reportVerifyRequest.setPosition(JsonUtils.toJSONString(record.getPosition()));

                Metadata metadata = Metadata.newBuilder().setType(ReportVerifyRequest.class.getSimpleName()).build();

                Payload request = Payload.newBuilder().setMetadata(metadata)
                        .setBody(
                                Any.newBuilder().setValue(UnsafeByteOperations.unsafeWrap(Objects.requireNonNull(JsonUtils.toJSONBytes(reportVerifyRequest))))
                                        .build())
                        .build();
                requestObserver.onNext(request);
            } catch (Exception e) {
                log.error("Failed to report verify request", e);
            }
        });
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : messageDigest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public void stop() {
        reportVerifyExecutor.shutdown();
        if (requestObserver != null) {
            requestObserver.onCompleted();
        }
    }

}
