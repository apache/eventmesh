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
    value = "by gRPC proto compiler (version 1.43.2)",
    comments = "Source: eventmesh-service.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class ConsumerServiceGrpc {

  private ConsumerServiceGrpc() {}

  public static final String SERVICE_NAME = "org.apache.eventmesh.cloudevents.v1.ConsumerService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<CloudEvent,
      CloudEvent> getSubscribeMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "subscribe",
      requestType = CloudEvent.class,
      responseType = CloudEvent.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<CloudEvent,
      CloudEvent> getSubscribeMethod() {
    io.grpc.MethodDescriptor<CloudEvent, CloudEvent> getSubscribeMethod;
    if ((getSubscribeMethod = ConsumerServiceGrpc.getSubscribeMethod) == null) {
      synchronized (ConsumerServiceGrpc.class) {
        if ((getSubscribeMethod = ConsumerServiceGrpc.getSubscribeMethod) == null) {
          ConsumerServiceGrpc.getSubscribeMethod = getSubscribeMethod =
              io.grpc.MethodDescriptor.<CloudEvent, CloudEvent>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "subscribe"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  CloudEvent.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  CloudEvent.getDefaultInstance()))
              .setSchemaDescriptor(new ConsumerServiceMethodDescriptorSupplier("subscribe"))
              .build();
        }
      }
    }
    return getSubscribeMethod;
  }

  private static volatile io.grpc.MethodDescriptor<CloudEvent,
      CloudEvent> getSubscribeStreamMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "subscribeStream",
      requestType = CloudEvent.class,
      responseType = CloudEvent.class,
      methodType = io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
  public static io.grpc.MethodDescriptor<CloudEvent,
      CloudEvent> getSubscribeStreamMethod() {
    io.grpc.MethodDescriptor<CloudEvent, CloudEvent> getSubscribeStreamMethod;
    if ((getSubscribeStreamMethod = ConsumerServiceGrpc.getSubscribeStreamMethod) == null) {
      synchronized (ConsumerServiceGrpc.class) {
        if ((getSubscribeStreamMethod = ConsumerServiceGrpc.getSubscribeStreamMethod) == null) {
          ConsumerServiceGrpc.getSubscribeStreamMethod = getSubscribeStreamMethod =
              io.grpc.MethodDescriptor.<CloudEvent, CloudEvent>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "subscribeStream"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  CloudEvent.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  CloudEvent.getDefaultInstance()))
              .setSchemaDescriptor(new ConsumerServiceMethodDescriptorSupplier("subscribeStream"))
              .build();
        }
      }
    }
    return getSubscribeStreamMethod;
  }

  private static volatile io.grpc.MethodDescriptor<CloudEvent,
      CloudEvent> getUnsubscribeMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "unsubscribe",
      requestType = CloudEvent.class,
      responseType = CloudEvent.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<CloudEvent,
      CloudEvent> getUnsubscribeMethod() {
    io.grpc.MethodDescriptor<CloudEvent, CloudEvent> getUnsubscribeMethod;
    if ((getUnsubscribeMethod = ConsumerServiceGrpc.getUnsubscribeMethod) == null) {
      synchronized (ConsumerServiceGrpc.class) {
        if ((getUnsubscribeMethod = ConsumerServiceGrpc.getUnsubscribeMethod) == null) {
          ConsumerServiceGrpc.getUnsubscribeMethod = getUnsubscribeMethod =
              io.grpc.MethodDescriptor.<CloudEvent, CloudEvent>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "unsubscribe"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  CloudEvent.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  CloudEvent.getDefaultInstance()))
              .setSchemaDescriptor(new ConsumerServiceMethodDescriptorSupplier("unsubscribe"))
              .build();
        }
      }
    }
    return getUnsubscribeMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static ConsumerServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ConsumerServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ConsumerServiceStub>() {
        @Override
        public ConsumerServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ConsumerServiceStub(channel, callOptions);
        }
      };
    return ConsumerServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static ConsumerServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ConsumerServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ConsumerServiceBlockingStub>() {
        @Override
        public ConsumerServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ConsumerServiceBlockingStub(channel, callOptions);
        }
      };
    return ConsumerServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static ConsumerServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ConsumerServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ConsumerServiceFutureStub>() {
        @Override
        public ConsumerServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ConsumerServiceFutureStub(channel, callOptions);
        }
      };
    return ConsumerServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public static abstract class ConsumerServiceImplBase implements io.grpc.BindableService {

    /**
     * <pre>
     * The subscribed event will be delivered by invoking the webhook url in the Subscription
     * </pre>
     */
    public void subscribe(CloudEvent request,
        io.grpc.stub.StreamObserver<CloudEvent> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSubscribeMethod(), responseObserver);
    }

    /**
     * <pre>
     *  The subscribed event will be delivered through stream of Message
     * </pre>
     */
    public io.grpc.stub.StreamObserver<CloudEvent> subscribeStream(
        io.grpc.stub.StreamObserver<CloudEvent> responseObserver) {
      return io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall(getSubscribeStreamMethod(), responseObserver);
    }

    /**
     */
    public void unsubscribe(CloudEvent request,
        io.grpc.stub.StreamObserver<CloudEvent> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getUnsubscribeMethod(), responseObserver);
    }

    @Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getSubscribeMethod(),
            io.grpc.stub.ServerCalls.asyncUnaryCall(
              new MethodHandlers<
                CloudEvent,
                CloudEvent>(
                  this, METHODID_SUBSCRIBE)))
          .addMethod(
            getSubscribeStreamMethod(),
            io.grpc.stub.ServerCalls.asyncBidiStreamingCall(
              new MethodHandlers<
                CloudEvent,
                CloudEvent>(
                  this, METHODID_SUBSCRIBE_STREAM)))
          .addMethod(
            getUnsubscribeMethod(),
            io.grpc.stub.ServerCalls.asyncUnaryCall(
              new MethodHandlers<
                CloudEvent,
                CloudEvent>(
                  this, METHODID_UNSUBSCRIBE)))
          .build();
    }
  }

  /**
   */
  public static final class ConsumerServiceStub extends io.grpc.stub.AbstractAsyncStub<ConsumerServiceStub> {
    private ConsumerServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @Override
    protected ConsumerServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ConsumerServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * The subscribed event will be delivered by invoking the webhook url in the Subscription
     * </pre>
     */
    public void subscribe(CloudEvent request,
        io.grpc.stub.StreamObserver<CloudEvent> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSubscribeMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     *  The subscribed event will be delivered through stream of Message
     * </pre>
     */
    public io.grpc.stub.StreamObserver<CloudEvent> subscribeStream(
        io.grpc.stub.StreamObserver<CloudEvent> responseObserver) {
      return io.grpc.stub.ClientCalls.asyncBidiStreamingCall(
          getChannel().newCall(getSubscribeStreamMethod(), getCallOptions()), responseObserver);
    }

    /**
     */
    public void unsubscribe(CloudEvent request,
        io.grpc.stub.StreamObserver<CloudEvent> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getUnsubscribeMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   */
  public static final class ConsumerServiceBlockingStub extends io.grpc.stub.AbstractBlockingStub<ConsumerServiceBlockingStub> {
    private ConsumerServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @Override
    protected ConsumerServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ConsumerServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * The subscribed event will be delivered by invoking the webhook url in the Subscription
     * </pre>
     */
    public CloudEvent subscribe(CloudEvent request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSubscribeMethod(), getCallOptions(), request);
    }

    /**
     */
    public CloudEvent unsubscribe(CloudEvent request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getUnsubscribeMethod(), getCallOptions(), request);
    }
  }

  /**
   */
  public static final class ConsumerServiceFutureStub extends io.grpc.stub.AbstractFutureStub<ConsumerServiceFutureStub> {
    private ConsumerServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @Override
    protected ConsumerServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ConsumerServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * The subscribed event will be delivered by invoking the webhook url in the Subscription
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<CloudEvent> subscribe(
        CloudEvent request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSubscribeMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<CloudEvent> unsubscribe(
        CloudEvent request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getUnsubscribeMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_SUBSCRIBE = 0;
  private static final int METHODID_UNSUBSCRIBE = 1;
  private static final int METHODID_SUBSCRIBE_STREAM = 2;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final ConsumerServiceImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(ConsumerServiceImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_SUBSCRIBE:
          serviceImpl.subscribe((CloudEvent) request,
              (io.grpc.stub.StreamObserver<CloudEvent>) responseObserver);
          break;
        case METHODID_UNSUBSCRIBE:
          serviceImpl.unsubscribe((CloudEvent) request,
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
        case METHODID_SUBSCRIBE_STREAM:
          return (io.grpc.stub.StreamObserver<Req>) serviceImpl.subscribeStream(
              (io.grpc.stub.StreamObserver<CloudEvent>) responseObserver);
        default:
          throw new AssertionError();
      }
    }
  }

  private static abstract class ConsumerServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    ConsumerServiceBaseDescriptorSupplier() {}

    @Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return EventMeshGrpcService.getDescriptor();
    }

    @Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("ConsumerService");
    }
  }

  private static final class ConsumerServiceFileDescriptorSupplier
      extends ConsumerServiceBaseDescriptorSupplier {
    ConsumerServiceFileDescriptorSupplier() {}
  }

  private static final class ConsumerServiceMethodDescriptorSupplier
      extends ConsumerServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    ConsumerServiceMethodDescriptorSupplier(String methodName) {
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
      synchronized (ConsumerServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new ConsumerServiceFileDescriptorSupplier())
              .addMethod(getSubscribeMethod())
              .addMethod(getSubscribeStreamMethod())
              .addMethod(getUnsubscribeMethod())
              .build();
        }
      }
    }
    return result;
  }
}
