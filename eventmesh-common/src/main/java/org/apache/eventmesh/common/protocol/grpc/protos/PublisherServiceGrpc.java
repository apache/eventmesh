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

package org.apache.eventmesh.common.protocol.grpc.protos;

import static io.grpc.MethodDescriptor.generateFullMethodName;
import static io.grpc.stub.ClientCalls.asyncUnaryCall;
import static io.grpc.stub.ClientCalls.blockingUnaryCall;
import static io.grpc.stub.ClientCalls.futureUnaryCall;
import static io.grpc.stub.ServerCalls.asyncUnaryCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall;

/**
 */
@SuppressWarnings({"all"})
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.15.0)",
    comments = "Source: eventmesh-client.proto")
public final class PublisherServiceGrpc {

  private PublisherServiceGrpc() {}

  public static final String SERVICE_NAME = "eventmesh.common.protocol.grpc.PublisherService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<SimpleMessage,
      Response> getPublishMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "publish",
      requestType = SimpleMessage.class,
      responseType = Response.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<SimpleMessage,
      Response> getPublishMethod() {
    io.grpc.MethodDescriptor<SimpleMessage, Response> getPublishMethod;
    if ((getPublishMethod = PublisherServiceGrpc.getPublishMethod) == null) {
      synchronized (PublisherServiceGrpc.class) {
        if ((getPublishMethod = PublisherServiceGrpc.getPublishMethod) == null) {
          PublisherServiceGrpc.getPublishMethod = getPublishMethod = 
              io.grpc.MethodDescriptor.<SimpleMessage, Response>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(
                  "eventmesh.common.protocol.grpc.PublisherService", "publish"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  SimpleMessage.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  Response.getDefaultInstance()))
                  .setSchemaDescriptor(new PublisherServiceMethodDescriptorSupplier("publish"))
                  .build();
          }
        }
     }
     return getPublishMethod;
  }

  private static volatile io.grpc.MethodDescriptor<SimpleMessage,
      SimpleMessage> getRequestReplyMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "requestReply",
      requestType = SimpleMessage.class,
      responseType = SimpleMessage.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<SimpleMessage,
      SimpleMessage> getRequestReplyMethod() {
    io.grpc.MethodDescriptor<SimpleMessage, SimpleMessage> getRequestReplyMethod;
    if ((getRequestReplyMethod = PublisherServiceGrpc.getRequestReplyMethod) == null) {
      synchronized (PublisherServiceGrpc.class) {
        if ((getRequestReplyMethod = PublisherServiceGrpc.getRequestReplyMethod) == null) {
          PublisherServiceGrpc.getRequestReplyMethod = getRequestReplyMethod = 
              io.grpc.MethodDescriptor.<SimpleMessage, SimpleMessage>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(
                  "eventmesh.common.protocol.grpc.PublisherService", "requestReply"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  SimpleMessage.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  SimpleMessage.getDefaultInstance()))
                  .setSchemaDescriptor(new PublisherServiceMethodDescriptorSupplier("requestReply"))
                  .build();
          }
        }
     }
     return getRequestReplyMethod;
  }

  private static volatile io.grpc.MethodDescriptor<BatchMessage,
      Response> getBatchPublishMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "batchPublish",
      requestType = BatchMessage.class,
      responseType = Response.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<BatchMessage,
      Response> getBatchPublishMethod() {
    io.grpc.MethodDescriptor<BatchMessage, Response> getBatchPublishMethod;
    if ((getBatchPublishMethod = PublisherServiceGrpc.getBatchPublishMethod) == null) {
      synchronized (PublisherServiceGrpc.class) {
        if ((getBatchPublishMethod = PublisherServiceGrpc.getBatchPublishMethod) == null) {
          PublisherServiceGrpc.getBatchPublishMethod = getBatchPublishMethod = 
              io.grpc.MethodDescriptor.<BatchMessage, Response>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(
                  "eventmesh.common.protocol.grpc.PublisherService", "batchPublish"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  BatchMessage.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  Response.getDefaultInstance()))
                  .setSchemaDescriptor(new PublisherServiceMethodDescriptorSupplier("batchPublish"))
                  .build();
          }
        }
     }
     return getBatchPublishMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static PublisherServiceStub newStub(io.grpc.Channel channel) {
    return new PublisherServiceStub(channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static PublisherServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    return new PublisherServiceBlockingStub(channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static PublisherServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    return new PublisherServiceFutureStub(channel);
  }

  /**
   */
  public static abstract class PublisherServiceImplBase implements io.grpc.BindableService {

    /**
     * <pre>
     * Async event publish
     * </pre>
     */
    public void publish(SimpleMessage request,
                        io.grpc.stub.StreamObserver<Response> responseObserver) {
      asyncUnimplementedUnaryCall(getPublishMethod(), responseObserver);
    }

    /**
     * <pre>
     * Sync event publish
     * </pre>
     */
    public void requestReply(SimpleMessage request,
                             io.grpc.stub.StreamObserver<SimpleMessage> responseObserver) {
      asyncUnimplementedUnaryCall(getRequestReplyMethod(), responseObserver);
    }

    /**
     * <pre>
     * Async batch event publish
     * </pre>
     */
    public void batchPublish(BatchMessage request,
                             io.grpc.stub.StreamObserver<Response> responseObserver) {
      asyncUnimplementedUnaryCall(getBatchPublishMethod(), responseObserver);
    }

    @Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getPublishMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                SimpleMessage,
                Response>(
                  this, METHODID_PUBLISH)))
          .addMethod(
            getRequestReplyMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                SimpleMessage,
                SimpleMessage>(
                  this, METHODID_REQUEST_REPLY)))
          .addMethod(
            getBatchPublishMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                BatchMessage,
                Response>(
                  this, METHODID_BATCH_PUBLISH)))
          .build();
    }
  }

  /**
   */
  public static final class PublisherServiceStub extends io.grpc.stub.AbstractStub<PublisherServiceStub> {
    private PublisherServiceStub(io.grpc.Channel channel) {
      super(channel);
    }

    private PublisherServiceStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @Override
    protected PublisherServiceStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new PublisherServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * Async event publish
     * </pre>
     */
    public void publish(SimpleMessage request,
                        io.grpc.stub.StreamObserver<Response> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getPublishMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Sync event publish
     * </pre>
     */
    public void requestReply(SimpleMessage request,
                             io.grpc.stub.StreamObserver<SimpleMessage> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getRequestReplyMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Async batch event publish
     * </pre>
     */
    public void batchPublish(BatchMessage request,
                             io.grpc.stub.StreamObserver<Response> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getBatchPublishMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   */
  public static final class PublisherServiceBlockingStub extends io.grpc.stub.AbstractStub<PublisherServiceBlockingStub> {
    private PublisherServiceBlockingStub(io.grpc.Channel channel) {
      super(channel);
    }

    private PublisherServiceBlockingStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @Override
    protected PublisherServiceBlockingStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new PublisherServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Async event publish
     * </pre>
     */
    public Response publish(SimpleMessage request) {
      return blockingUnaryCall(
          getChannel(), getPublishMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Sync event publish
     * </pre>
     */
    public SimpleMessage requestReply(SimpleMessage request) {
      return blockingUnaryCall(
          getChannel(), getRequestReplyMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Async batch event publish
     * </pre>
     */
    public Response batchPublish(BatchMessage request) {
      return blockingUnaryCall(
          getChannel(), getBatchPublishMethod(), getCallOptions(), request);
    }
  }

  /**
   */
  public static final class PublisherServiceFutureStub extends io.grpc.stub.AbstractStub<PublisherServiceFutureStub> {
    private PublisherServiceFutureStub(io.grpc.Channel channel) {
      super(channel);
    }

    private PublisherServiceFutureStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @Override
    protected PublisherServiceFutureStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new PublisherServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Async event publish
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<Response> publish(
        SimpleMessage request) {
      return futureUnaryCall(
          getChannel().newCall(getPublishMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Sync event publish
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<SimpleMessage> requestReply(
        SimpleMessage request) {
      return futureUnaryCall(
          getChannel().newCall(getRequestReplyMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Async batch event publish
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<Response> batchPublish(
        BatchMessage request) {
      return futureUnaryCall(
          getChannel().newCall(getBatchPublishMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_PUBLISH = 0;
  private static final int METHODID_REQUEST_REPLY = 1;
  private static final int METHODID_BATCH_PUBLISH = 2;

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
          serviceImpl.publish((SimpleMessage) request,
              (io.grpc.stub.StreamObserver<Response>) responseObserver);
          break;
        case METHODID_REQUEST_REPLY:
          serviceImpl.requestReply((SimpleMessage) request,
              (io.grpc.stub.StreamObserver<SimpleMessage>) responseObserver);
          break;
        case METHODID_BATCH_PUBLISH:
          serviceImpl.batchPublish((BatchMessage) request,
              (io.grpc.stub.StreamObserver<Response>) responseObserver);
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
      return EventmeshGrpc.getDescriptor();
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
              .addMethod(getBatchPublishMethod())
              .build();
        }
      }
    }
    return result;
  }
}
