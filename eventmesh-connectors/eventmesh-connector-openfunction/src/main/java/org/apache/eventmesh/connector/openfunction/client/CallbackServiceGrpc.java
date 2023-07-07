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

package org.apache.eventmesh.connector.openfunction.client;

import static io.grpc.MethodDescriptor.generateFullMethodName;
import static io.grpc.stub.ClientCalls.asyncUnaryCall;
import static io.grpc.stub.ClientCalls.blockingUnaryCall;
import static io.grpc.stub.ClientCalls.futureUnaryCall;
import static io.grpc.stub.ServerCalls.asyncUnaryCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall;

import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;

/**
 *
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.4.0)",
    comments = "Source: callback-service.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class CallbackServiceGrpc {

    private CallbackServiceGrpc() {
    }

    public static final String SERVICE_NAME = "org.apache.eventmesh.cloudevents.v1.CallbackService";

    // Static method descriptors that strictly reflect the proto.
    @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/1901")
    public static final io.grpc.MethodDescriptor<CloudEvent,
        CloudEvent> METHOD_ON_TOPIC_EVENT =
        io.grpc.MethodDescriptor.<CloudEvent, CloudEvent>newBuilder()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(generateFullMethodName(
                "org.apache.eventmesh.cloudevents.v1.CallbackService", "OnTopicEvent"))
            .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                CloudEvent.getDefaultInstance()))
            .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                CloudEvent.getDefaultInstance()))
            .build();

    /**
     * Creates a new async stub that supports all call types for the service
     */
    public static CallbackServiceStub newStub(io.grpc.Channel channel) {
        return new CallbackServiceStub(channel);
    }

    /**
     * Creates a new blocking-style stub that supports unary and streaming output calls on the service
     */
    public static CallbackServiceBlockingStub newBlockingStub(
        io.grpc.Channel channel) {
        return new CallbackServiceBlockingStub(channel);
    }

    /**
     * Creates a new ListenableFuture-style stub that supports unary calls on the service
     */
    public static CallbackServiceFutureStub newFutureStub(
        io.grpc.Channel channel) {
        return new CallbackServiceFutureStub(channel);
    }

    /**
     *
     */
    public static abstract class CallbackServiceImplBase implements io.grpc.BindableService {

        /**
         * <pre>
         * Subscribes events
         * </pre>
         */
        public void onTopicEvent(CloudEvent request,
            io.grpc.stub.StreamObserver<CloudEvent> responseObserver) {
            asyncUnimplementedUnaryCall(METHOD_ON_TOPIC_EVENT, responseObserver);
        }

        @Override
        public final io.grpc.ServerServiceDefinition bindService() {
            return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
                .addMethod(
                    METHOD_ON_TOPIC_EVENT,
                    asyncUnaryCall(
                        new MethodHandlers<
                            CloudEvent,
                            CloudEvent>(
                            this, METHODID_ON_TOPIC_EVENT)))
                .build();
        }
    }

    /**
     *
     */
    public static final class CallbackServiceStub extends io.grpc.stub.AbstractStub<CallbackServiceStub> {

        private CallbackServiceStub(io.grpc.Channel channel) {
            super(channel);
        }

        private CallbackServiceStub(io.grpc.Channel channel,
            io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @Override
        protected CallbackServiceStub build(io.grpc.Channel channel,
            io.grpc.CallOptions callOptions) {
            return new CallbackServiceStub(channel, callOptions);
        }

        /**
         * <pre>
         * Subscribes events
         * </pre>
         */
        public void onTopicEvent(CloudEvent request,
            io.grpc.stub.StreamObserver<CloudEvent> responseObserver) {
            asyncUnaryCall(
                getChannel().newCall(METHOD_ON_TOPIC_EVENT, getCallOptions()), request, responseObserver);
        }
    }

    /**
     *
     */
    public static final class CallbackServiceBlockingStub extends io.grpc.stub.AbstractStub<CallbackServiceBlockingStub> {

        private CallbackServiceBlockingStub(io.grpc.Channel channel) {
            super(channel);
        }

        private CallbackServiceBlockingStub(io.grpc.Channel channel,
            io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @Override
        protected CallbackServiceBlockingStub build(io.grpc.Channel channel,
            io.grpc.CallOptions callOptions) {
            return new CallbackServiceBlockingStub(channel, callOptions);
        }

        /**
         * <pre>
         * Subscribes events
         * </pre>
         */
        public CloudEvent onTopicEvent(
            CloudEvent request) {
            return blockingUnaryCall(
                getChannel(), METHOD_ON_TOPIC_EVENT, getCallOptions(), request);
        }
    }

    /**
     *
     */
    public static final class CallbackServiceFutureStub extends io.grpc.stub.AbstractStub<CallbackServiceFutureStub> {

        private CallbackServiceFutureStub(io.grpc.Channel channel) {
            super(channel);
        }

        private CallbackServiceFutureStub(io.grpc.Channel channel,
            io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @Override
        protected CallbackServiceFutureStub build(io.grpc.Channel channel,
            io.grpc.CallOptions callOptions) {
            return new CallbackServiceFutureStub(channel, callOptions);
        }

        /**
         * <pre>
         * Subscribes events
         * </pre>
         */
        public com.google.common.util.concurrent.ListenableFuture<CloudEvent> onTopicEvent(
            CloudEvent request) {
            return futureUnaryCall(
                getChannel().newCall(METHOD_ON_TOPIC_EVENT, getCallOptions()), request);
        }
    }

    private static final int METHODID_ON_TOPIC_EVENT = 0;

    private static final class MethodHandlers<Req, Resp> implements
        io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
        io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
        io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
        io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {

        private final CallbackServiceImplBase serviceImpl;
        private final int methodId;

        MethodHandlers(CallbackServiceImplBase serviceImpl, int methodId) {
            this.serviceImpl = serviceImpl;
            this.methodId = methodId;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
            switch (methodId) {
                case METHODID_ON_TOPIC_EVENT:
                    serviceImpl.onTopicEvent((CloudEvent) request,
                        (io.grpc.stub.StreamObserver<CloudEvent>) responseObserver);
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

    private static final class CallbackServiceDescriptorSupplier implements io.grpc.protobuf.ProtoFileDescriptorSupplier {

        @Override
        public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
            return EventMeshGrpcService.getDescriptor();
        }
    }

    private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

    public static io.grpc.ServiceDescriptor getServiceDescriptor() {
        io.grpc.ServiceDescriptor result = serviceDescriptor;
        if (result == null) {
            synchronized (CallbackServiceGrpc.class) {
                result = serviceDescriptor;
                if (result == null) {
                    serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
                        .setSchemaDescriptor(new CallbackServiceDescriptorSupplier())
                        .addMethod(METHOD_ON_TOPIC_EVENT)
                        .build();
                }
            }
        }
        return result;
    }
}
