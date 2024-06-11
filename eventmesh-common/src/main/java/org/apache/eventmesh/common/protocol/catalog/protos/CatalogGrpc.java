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

package org.apache.eventmesh.common.protocol.catalog.protos;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 *
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.49.1-SNAPSHOT)",
    comments = "Source: catalog.proto")
@io.grpc.stub.annotations.GrpcGenerated
@SuppressWarnings({"all"})
public final class CatalogGrpc {

    private CatalogGrpc() {
    }

    public static final String SERVICE_NAME = "eventmesh.catalog.api.protocol.Catalog";

    // Static method descriptors that strictly reflect the proto.
    private static volatile io.grpc.MethodDescriptor<RegistryRequest,
        RegistryResponse> getRegistryMethod;

    @io.grpc.stub.annotations.RpcMethod(
        fullMethodName = SERVICE_NAME + '/' + "Registry",
        requestType = RegistryRequest.class,
        responseType = RegistryResponse.class,
        methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
    public static io.grpc.MethodDescriptor<RegistryRequest,
        RegistryResponse> getRegistryMethod() {
        io.grpc.MethodDescriptor<RegistryRequest, RegistryResponse> getRegistryMethod;
        if ((getRegistryMethod = CatalogGrpc.getRegistryMethod) == null) {
            synchronized (CatalogGrpc.class) {
                if ((getRegistryMethod = CatalogGrpc.getRegistryMethod) == null) {
                    CatalogGrpc.getRegistryMethod = getRegistryMethod =
                        io.grpc.MethodDescriptor.<RegistryRequest, RegistryResponse>newBuilder()
                            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                            .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Registry"))
                            .setSampledToLocalTracing(true)
                            .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                RegistryRequest.getDefaultInstance()))
                            .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                RegistryResponse.getDefaultInstance()))
                            .setSchemaDescriptor(new CatalogMethodDescriptorSupplier("Registry"))
                            .build();
                }
            }
        }
        return getRegistryMethod;
    }

    private static volatile io.grpc.MethodDescriptor<QueryOperationsRequest,
        QueryOperationsResponse> getQueryOperationsMethod;

    @io.grpc.stub.annotations.RpcMethod(
        fullMethodName = SERVICE_NAME + '/' + "QueryOperations",
        requestType = QueryOperationsRequest.class,
        responseType = QueryOperationsResponse.class,
        methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
    public static io.grpc.MethodDescriptor<QueryOperationsRequest,
        QueryOperationsResponse> getQueryOperationsMethod() {
        io.grpc.MethodDescriptor<QueryOperationsRequest, QueryOperationsResponse> getQueryOperationsMethod;
        if ((getQueryOperationsMethod = CatalogGrpc.getQueryOperationsMethod) == null) {
            synchronized (CatalogGrpc.class) {
                if ((getQueryOperationsMethod = CatalogGrpc.getQueryOperationsMethod) == null) {
                    CatalogGrpc.getQueryOperationsMethod = getQueryOperationsMethod =
                        io.grpc.MethodDescriptor.<QueryOperationsRequest, QueryOperationsResponse>newBuilder()
                            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                            .setFullMethodName(generateFullMethodName(SERVICE_NAME, "QueryOperations"))
                            .setSampledToLocalTracing(true)
                            .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                QueryOperationsRequest.getDefaultInstance()))
                            .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                QueryOperationsResponse.getDefaultInstance()))
                            .setSchemaDescriptor(new CatalogMethodDescriptorSupplier("QueryOperations"))
                            .build();
                }
            }
        }
        return getQueryOperationsMethod;
    }

    /**
     * Creates a new async stub that supports all call types for the service
     */
    public static CatalogStub newStub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<CatalogStub> factory =
            new io.grpc.stub.AbstractStub.StubFactory<CatalogStub>() {
                @Override
                public CatalogStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                    return new CatalogStub(channel, callOptions);
                }
            };
        return CatalogStub.newStub(factory, channel);
    }

    /**
     * Creates a new blocking-style stub that supports unary and streaming output calls on the service
     */
    public static CatalogBlockingStub newBlockingStub(
        io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<CatalogBlockingStub> factory =
            new io.grpc.stub.AbstractStub.StubFactory<CatalogBlockingStub>() {
                @Override
                public CatalogBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                    return new CatalogBlockingStub(channel, callOptions);
                }
            };
        return CatalogBlockingStub.newStub(factory, channel);
    }

    /**
     * Creates a new ListenableFuture-style stub that supports unary calls on the service
     */
    public static CatalogFutureStub newFutureStub(
        io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<CatalogFutureStub> factory =
            new io.grpc.stub.AbstractStub.StubFactory<CatalogFutureStub>() {
                @Override
                public CatalogFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                    return new CatalogFutureStub(channel, callOptions);
                }
            };
        return CatalogFutureStub.newStub(factory, channel);
    }

    /**
     *
     */
    public static abstract class CatalogImplBase implements io.grpc.BindableService {

        /**
         *
         */
        public void registry(RegistryRequest request,
                             io.grpc.stub.StreamObserver<RegistryResponse> responseObserver) {
            io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getRegistryMethod(), responseObserver);
        }

        /**
         *
         */
        public void queryOperations(QueryOperationsRequest request,
                                    io.grpc.stub.StreamObserver<QueryOperationsResponse> responseObserver) {
            io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getQueryOperationsMethod(), responseObserver);
        }

        @Override
        public final io.grpc.ServerServiceDefinition bindService() {
            return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
                .addMethod(
                    getRegistryMethod(),
                    io.grpc.stub.ServerCalls.asyncUnaryCall(
                        new MethodHandlers<
                            RegistryRequest,
                            RegistryResponse>(
                            this, METHODID_REGISTRY)))
                .addMethod(
                    getQueryOperationsMethod(),
                    io.grpc.stub.ServerCalls.asyncUnaryCall(
                        new MethodHandlers<
                            QueryOperationsRequest,
                            QueryOperationsResponse>(
                            this, METHODID_QUERY_OPERATIONS)))
                .build();
        }
    }

    /**
     *
     */
    public static final class CatalogStub extends io.grpc.stub.AbstractAsyncStub<CatalogStub> {
        private CatalogStub(
            io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @Override
        protected CatalogStub build(
            io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new CatalogStub(channel, callOptions);
        }

        /**
         *
         */
        public void registry(RegistryRequest request,
                             io.grpc.stub.StreamObserver<RegistryResponse> responseObserver) {
            io.grpc.stub.ClientCalls.asyncUnaryCall(
                getChannel().newCall(getRegistryMethod(), getCallOptions()), request, responseObserver);
        }

        /**
         *
         */
        public void queryOperations(QueryOperationsRequest request,
                                    io.grpc.stub.StreamObserver<QueryOperationsResponse> responseObserver) {
            io.grpc.stub.ClientCalls.asyncUnaryCall(
                getChannel().newCall(getQueryOperationsMethod(), getCallOptions()), request, responseObserver);
        }
    }

    /**
     *
     */
    public static final class CatalogBlockingStub extends io.grpc.stub.AbstractBlockingStub<CatalogBlockingStub> {
        private CatalogBlockingStub(
            io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @Override
        protected CatalogBlockingStub build(
            io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new CatalogBlockingStub(channel, callOptions);
        }

        /**
         *
         */
        public RegistryResponse registry(RegistryRequest request) {
            return io.grpc.stub.ClientCalls.blockingUnaryCall(
                getChannel(), getRegistryMethod(), getCallOptions(), request);
        }

        /**
         *
         */
        public QueryOperationsResponse queryOperations(QueryOperationsRequest request) {
            return io.grpc.stub.ClientCalls.blockingUnaryCall(
                getChannel(), getQueryOperationsMethod(), getCallOptions(), request);
        }
    }

    /**
     *
     */
    public static final class CatalogFutureStub extends io.grpc.stub.AbstractFutureStub<CatalogFutureStub> {
        private CatalogFutureStub(
            io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @Override
        protected CatalogFutureStub build(
            io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new CatalogFutureStub(channel, callOptions);
        }

        /**
         *
         */
        public com.google.common.util.concurrent.ListenableFuture<RegistryResponse> registry(
            RegistryRequest request) {
            return io.grpc.stub.ClientCalls.futureUnaryCall(
                getChannel().newCall(getRegistryMethod(), getCallOptions()), request);
        }

        /**
         *
         */
        public com.google.common.util.concurrent.ListenableFuture<QueryOperationsResponse> queryOperations(
            QueryOperationsRequest request) {
            return io.grpc.stub.ClientCalls.futureUnaryCall(
                getChannel().newCall(getQueryOperationsMethod(), getCallOptions()), request);
        }
    }

    private static final int METHODID_REGISTRY = 0;
    private static final int METHODID_QUERY_OPERATIONS = 1;

    private static final class MethodHandlers<Req, Resp> implements
        io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
        io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
        io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
        io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
        private final CatalogImplBase serviceImpl;
        private final int methodId;

        MethodHandlers(CatalogImplBase serviceImpl, int methodId) {
            this.serviceImpl = serviceImpl;
            this.methodId = methodId;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
            switch (methodId) {
                case METHODID_REGISTRY:
                    serviceImpl.registry((RegistryRequest) request,
                        (io.grpc.stub.StreamObserver<RegistryResponse>) responseObserver);
                    break;
                case METHODID_QUERY_OPERATIONS:
                    serviceImpl.queryOperations((QueryOperationsRequest) request,
                        (io.grpc.stub.StreamObserver<QueryOperationsResponse>) responseObserver);
                    break;
                default:
                    throw new AssertionError();
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public io.grpc.stub.StreamObserver<Req> invoke(
            io.grpc.stub.StreamObserver<Resp> responseObserver) {
            switch (methodId) {
                default:
                    throw new AssertionError();
            }
        }
    }

    private static abstract class CatalogBaseDescriptorSupplier
        implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
        CatalogBaseDescriptorSupplier() {
        }

        @Override
        public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
            return EventmeshCatalogGrpc.getDescriptor();
        }

        @Override
        public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
            return getFileDescriptor().findServiceByName("Catalog");
        }
    }

    private static final class CatalogFileDescriptorSupplier
        extends CatalogBaseDescriptorSupplier {
        CatalogFileDescriptorSupplier() {
        }
    }

    private static final class CatalogMethodDescriptorSupplier
        extends CatalogBaseDescriptorSupplier
        implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
        private final String methodName;

        CatalogMethodDescriptorSupplier(String methodName) {
            this.methodName = methodName;
        }

        @Override
        public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
            return getServiceDescriptor().findMethodByName(methodName);
        }
    }

    private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

    public static io.grpc.ServiceDescriptor getServiceDescriptor() {
        io.grpc.ServiceDescriptor result = serviceDescriptor;
        if (result == null) {
            synchronized (CatalogGrpc.class) {
                result = serviceDescriptor;
                if (result == null) {
                    serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
                        .setSchemaDescriptor(new CatalogFileDescriptorSupplier())
                        .addMethod(getRegistryMethod())
                        .addMethod(getQueryOperationsMethod())
                        .build();
                }
            }
        }
        return result;
    }
}
