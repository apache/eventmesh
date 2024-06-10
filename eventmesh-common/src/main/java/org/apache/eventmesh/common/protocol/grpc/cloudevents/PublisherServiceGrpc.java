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

package org.apache.eventmesh.common.protocol.grpc.cloudevents;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.64.0)",
    comments = "Source: eventmesh-service.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class PublisherServiceGrpc {

  private PublisherServiceGrpc() {}

  public static final String SERVICE_NAME = "org.apache.eventmesh.cloudevents.v1.PublisherService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<CloudEvent,
      CloudEvent> getPublishMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "publish",
      requestType = CloudEvent.class,
      responseType = CloudEvent.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<CloudEvent,
      CloudEvent> getPublishMethod() {
    io.grpc.MethodDescriptor<CloudEvent, CloudEvent> getPublishMethod;
    if ((getPublishMethod = PublisherServiceGrpc.getPublishMethod) == null) {
      synchronized (PublisherServiceGrpc.class) {
        if ((getPublishMethod = PublisherServiceGrpc.getPublishMethod) == null) {
          PublisherServiceGrpc.getPublishMethod = getPublishMethod =
              io.grpc.MethodDescriptor.<CloudEvent, CloudEvent>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "publish"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  CloudEvent.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  CloudEvent.getDefaultInstance()))
              .setSchemaDescriptor(new PublisherServiceMethodDescriptorSupplier("publish"))
              .build();
        }
      }
    }
    return getPublishMethod;
  }

  private static volatile io.grpc.MethodDescriptor<CloudEvent,
      CloudEvent> getRequestReplyMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "requestReply",
      requestType = CloudEvent.class,
      responseType = CloudEvent.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<CloudEvent,
      CloudEvent> getRequestReplyMethod() {
    io.grpc.MethodDescriptor<CloudEvent, CloudEvent> getRequestReplyMethod;
    if ((getRequestReplyMethod = PublisherServiceGrpc.getRequestReplyMethod) == null) {
      synchronized (PublisherServiceGrpc.class) {
        if ((getRequestReplyMethod = PublisherServiceGrpc.getRequestReplyMethod) == null) {
          PublisherServiceGrpc.getRequestReplyMethod = getRequestReplyMethod =
              io.grpc.MethodDescriptor.<CloudEvent, CloudEvent>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "requestReply"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  CloudEvent.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  CloudEvent.getDefaultInstance()))
              .setSchemaDescriptor(new PublisherServiceMethodDescriptorSupplier("requestReply"))
              .build();
        }
      }
    }
    return getRequestReplyMethod;
  }

  private static volatile io.grpc.MethodDescriptor<CloudEvent,
      com.google.protobuf.Empty> getPublishOneWayMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "publishOneWay",
      requestType = CloudEvent.class,
      responseType = com.google.protobuf.Empty.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<CloudEvent,
      com.google.protobuf.Empty> getPublishOneWayMethod() {
    io.grpc.MethodDescriptor<CloudEvent, com.google.protobuf.Empty> getPublishOneWayMethod;
    if ((getPublishOneWayMethod = PublisherServiceGrpc.getPublishOneWayMethod) == null) {
      synchronized (PublisherServiceGrpc.class) {
        if ((getPublishOneWayMethod = PublisherServiceGrpc.getPublishOneWayMethod) == null) {
          PublisherServiceGrpc.getPublishOneWayMethod = getPublishOneWayMethod =
              io.grpc.MethodDescriptor.<CloudEvent, com.google.protobuf.Empty>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "publishOneWay"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  CloudEvent.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.google.protobuf.Empty.getDefaultInstance()))
              .setSchemaDescriptor(new PublisherServiceMethodDescriptorSupplier("publishOneWay"))
              .build();
        }
      }
    }
    return getPublishOneWayMethod;
  }

  private static volatile io.grpc.MethodDescriptor<CloudEventBatch,
      CloudEvent> getBatchPublishMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "batchPublish",
      requestType = CloudEventBatch.class,
      responseType = CloudEvent.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<CloudEventBatch,
      CloudEvent> getBatchPublishMethod() {
    io.grpc.MethodDescriptor<CloudEventBatch, CloudEvent> getBatchPublishMethod;
    if ((getBatchPublishMethod = PublisherServiceGrpc.getBatchPublishMethod) == null) {
      synchronized (PublisherServiceGrpc.class) {
        if ((getBatchPublishMethod = PublisherServiceGrpc.getBatchPublishMethod) == null) {
          PublisherServiceGrpc.getBatchPublishMethod = getBatchPublishMethod =
              io.grpc.MethodDescriptor.<CloudEventBatch, CloudEvent>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "batchPublish"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  CloudEventBatch.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  CloudEvent.getDefaultInstance()))
              .setSchemaDescriptor(new PublisherServiceMethodDescriptorSupplier("batchPublish"))
              .build();
        }
      }
    }
    return getBatchPublishMethod;
  }

  private static volatile io.grpc.MethodDescriptor<CloudEventBatch,
      com.google.protobuf.Empty> getBatchPublishOneWayMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "batchPublishOneWay",
      requestType = CloudEventBatch.class,
      responseType = com.google.protobuf.Empty.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<CloudEventBatch,
      com.google.protobuf.Empty> getBatchPublishOneWayMethod() {
    io.grpc.MethodDescriptor<CloudEventBatch, com.google.protobuf.Empty> getBatchPublishOneWayMethod;
    if ((getBatchPublishOneWayMethod = PublisherServiceGrpc.getBatchPublishOneWayMethod) == null) {
      synchronized (PublisherServiceGrpc.class) {
        if ((getBatchPublishOneWayMethod = PublisherServiceGrpc.getBatchPublishOneWayMethod) == null) {
          PublisherServiceGrpc.getBatchPublishOneWayMethod = getBatchPublishOneWayMethod =
              io.grpc.MethodDescriptor.<CloudEventBatch, com.google.protobuf.Empty>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "batchPublishOneWay"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  CloudEventBatch.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.google.protobuf.Empty.getDefaultInstance()))
              .setSchemaDescriptor(new PublisherServiceMethodDescriptorSupplier("batchPublishOneWay"))
              .build();
        }
      }
    }
    return getBatchPublishOneWayMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static PublisherServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PublisherServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PublisherServiceStub>() {
        @Override
        public PublisherServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PublisherServiceStub(channel, callOptions);
        }
      };
    return PublisherServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static PublisherServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PublisherServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PublisherServiceBlockingStub>() {
        @Override
        public PublisherServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PublisherServiceBlockingStub(channel, callOptions);
        }
      };
    return PublisherServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static PublisherServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PublisherServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PublisherServiceFutureStub>() {
        @Override
        public PublisherServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PublisherServiceFutureStub(channel, callOptions);
        }
      };
    return PublisherServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public static abstract class PublisherServiceImplBase implements io.grpc.BindableService {

    /**
     * <pre>
     *publish event
     * </pre>
     */
    public void publish(CloudEvent request,
        io.grpc.stub.StreamObserver<CloudEvent> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getPublishMethod(), responseObserver);
    }

    /**
     * <pre>
     *publish event with reply
     * </pre>
     */
    public void requestReply(CloudEvent request,
        io.grpc.stub.StreamObserver<CloudEvent> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getRequestReplyMethod(), responseObserver);
    }

    /**
     * <pre>
     *publish event one way
     * </pre>
     */
    public void publishOneWay(CloudEvent request,
        io.grpc.stub.StreamObserver<com.google.protobuf.Empty> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getPublishOneWayMethod(), responseObserver);
    }

    /**
     * <pre>
     * publish batch event
     * </pre>
     */
    public void batchPublish(CloudEventBatch request,
        io.grpc.stub.StreamObserver<CloudEvent> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getBatchPublishMethod(), responseObserver);
    }

    /**
     * <pre>
     *publish batch event one way
     * </pre>
     */
    public void batchPublishOneWay(CloudEventBatch request,
        io.grpc.stub.StreamObserver<com.google.protobuf.Empty> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getBatchPublishOneWayMethod(), responseObserver);
    }

    @Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getPublishMethod(),
            io.grpc.stub.ServerCalls.asyncUnaryCall(
              new MethodHandlers<
                CloudEvent,
                CloudEvent>(
                  this, METHODID_PUBLISH)))
          .addMethod(
            getRequestReplyMethod(),
            io.grpc.stub.ServerCalls.asyncUnaryCall(
              new MethodHandlers<
                CloudEvent,
                CloudEvent>(
                  this, METHODID_REQUEST_REPLY)))
          .addMethod(
            getPublishOneWayMethod(),
            io.grpc.stub.ServerCalls.asyncUnaryCall(
              new MethodHandlers<
                CloudEvent,
                com.google.protobuf.Empty>(
                  this, METHODID_PUBLISH_ONE_WAY)))
          .addMethod(
            getBatchPublishMethod(),
            io.grpc.stub.ServerCalls.asyncUnaryCall(
              new MethodHandlers<
                CloudEventBatch,
                CloudEvent>(
                  this, METHODID_BATCH_PUBLISH)))
          .addMethod(
            getBatchPublishOneWayMethod(),
            io.grpc.stub.ServerCalls.asyncUnaryCall(
              new MethodHandlers<
                CloudEventBatch,
                com.google.protobuf.Empty>(
                  this, METHODID_BATCH_PUBLISH_ONE_WAY)))
          .build();
    }
  }

  /**
   */
  public static final class PublisherServiceStub extends io.grpc.stub.AbstractAsyncStub<PublisherServiceStub> {
    private PublisherServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @Override
    protected PublisherServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PublisherServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     *publish event
     * </pre>
     */
    public void publish(CloudEvent request,
        io.grpc.stub.StreamObserver<CloudEvent> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getPublishMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     *publish event with reply
     * </pre>
     */
    public void requestReply(CloudEvent request,
        io.grpc.stub.StreamObserver<CloudEvent> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getRequestReplyMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     *publish event one way
     * </pre>
     */
    public void publishOneWay(CloudEvent request,
        io.grpc.stub.StreamObserver<com.google.protobuf.Empty> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getPublishOneWayMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * publish batch event
     * </pre>
     */
    public void batchPublish(CloudEventBatch request,
        io.grpc.stub.StreamObserver<CloudEvent> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getBatchPublishMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     *publish batch event one way
     * </pre>
     */
    public void batchPublishOneWay(CloudEventBatch request,
        io.grpc.stub.StreamObserver<com.google.protobuf.Empty> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getBatchPublishOneWayMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   */
  public static final class PublisherServiceBlockingStub extends io.grpc.stub.AbstractBlockingStub<PublisherServiceBlockingStub> {
    private PublisherServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @Override
    protected PublisherServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PublisherServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     *publish event
     * </pre>
     */
    public CloudEvent publish(CloudEvent request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getPublishMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     *publish event with reply
     * </pre>
     */
    public CloudEvent requestReply(CloudEvent request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getRequestReplyMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     *publish event one way
     * </pre>
     */
    public com.google.protobuf.Empty publishOneWay(CloudEvent request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getPublishOneWayMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * publish batch event
     * </pre>
     */
    public CloudEvent batchPublish(CloudEventBatch request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getBatchPublishMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     *publish batch event one way
     * </pre>
     */
    public com.google.protobuf.Empty batchPublishOneWay(CloudEventBatch request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getBatchPublishOneWayMethod(), getCallOptions(), request);
    }
  }

  /**
   */
  public static final class PublisherServiceFutureStub extends io.grpc.stub.AbstractFutureStub<PublisherServiceFutureStub> {
    private PublisherServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @Override
    protected PublisherServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PublisherServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     *publish event
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<CloudEvent> publish(
        CloudEvent request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getPublishMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     *publish event with reply
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<CloudEvent> requestReply(
        CloudEvent request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getRequestReplyMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     *publish event one way
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.google.protobuf.Empty> publishOneWay(
        CloudEvent request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getPublishOneWayMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * publish batch event
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<CloudEvent> batchPublish(
        CloudEventBatch request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getBatchPublishMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     *publish batch event one way
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.google.protobuf.Empty> batchPublishOneWay(
        CloudEventBatch request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getBatchPublishOneWayMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_PUBLISH = 0;
  private static final int METHODID_REQUEST_REPLY = 1;
  private static final int METHODID_PUBLISH_ONE_WAY = 2;
  private static final int METHODID_BATCH_PUBLISH = 3;
  private static final int METHODID_BATCH_PUBLISH_ONE_WAY = 4;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final PublisherServiceImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(PublisherServiceImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_PUBLISH:
          serviceImpl.publish((CloudEvent) request,
              (io.grpc.stub.StreamObserver<CloudEvent>) responseObserver);
          break;
        case METHODID_REQUEST_REPLY:
          serviceImpl.requestReply((CloudEvent) request,
              (io.grpc.stub.StreamObserver<CloudEvent>) responseObserver);
          break;
        case METHODID_PUBLISH_ONE_WAY:
          serviceImpl.publishOneWay((CloudEvent) request,
              (io.grpc.stub.StreamObserver<com.google.protobuf.Empty>) responseObserver);
          break;
        case METHODID_BATCH_PUBLISH:
          serviceImpl.batchPublish((CloudEventBatch) request,
              (io.grpc.stub.StreamObserver<CloudEvent>) responseObserver);
          break;
        case METHODID_BATCH_PUBLISH_ONE_WAY:
          serviceImpl.batchPublishOneWay((CloudEventBatch) request,
              (io.grpc.stub.StreamObserver<com.google.protobuf.Empty>) responseObserver);
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

  private static abstract class PublisherServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    PublisherServiceBaseDescriptorSupplier() {}

    @Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return EventMeshGrpcService.getDescriptor();
    }

    @Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("PublisherService");
    }
  }

  private static final class PublisherServiceFileDescriptorSupplier
      extends PublisherServiceBaseDescriptorSupplier {
    PublisherServiceFileDescriptorSupplier() {}
  }

  private static final class PublisherServiceMethodDescriptorSupplier
      extends PublisherServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    PublisherServiceMethodDescriptorSupplier(String methodName) {
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
      synchronized (PublisherServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new PublisherServiceFileDescriptorSupplier())
              .addMethod(getPublishMethod())
              .addMethod(getRequestReplyMethod())
              .addMethod(getPublishOneWayMethod())
              .addMethod(getBatchPublishMethod())
              .addMethod(getBatchPublishOneWayMethod())
              .build();
        }
      }
    }
    return result;
  }
}
