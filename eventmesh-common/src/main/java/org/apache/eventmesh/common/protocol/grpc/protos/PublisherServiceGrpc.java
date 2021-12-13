package org.apache.eventmesh.common.protocol.grpc.protos;

import static io.grpc.MethodDescriptor.generateFullMethodName;
import static io.grpc.stub.ClientCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ClientCalls.asyncClientStreamingCall;
import static io.grpc.stub.ClientCalls.asyncServerStreamingCall;
import static io.grpc.stub.ClientCalls.asyncUnaryCall;
import static io.grpc.stub.ClientCalls.blockingServerStreamingCall;
import static io.grpc.stub.ClientCalls.blockingUnaryCall;
import static io.grpc.stub.ClientCalls.futureUnaryCall;
import static io.grpc.stub.ServerCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ServerCalls.asyncClientStreamingCall;
import static io.grpc.stub.ServerCalls.asyncServerStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnaryCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.15.0)",
    comments = "Source: eventmesh-client.proto")
public final class PublisherServiceGrpc {

  private PublisherServiceGrpc() {}

  public static final String SERVICE_NAME = "eventmesh.common.protocol.grpc.PublisherService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage,
      org.apache.eventmesh.common.protocol.grpc.protos.Response> getPublishMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "publish",
      requestType = org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage.class,
      responseType = org.apache.eventmesh.common.protocol.grpc.protos.Response.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage,
      org.apache.eventmesh.common.protocol.grpc.protos.Response> getPublishMethod() {
    io.grpc.MethodDescriptor<org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage, org.apache.eventmesh.common.protocol.grpc.protos.Response> getPublishMethod;
    if ((getPublishMethod = PublisherServiceGrpc.getPublishMethod) == null) {
      synchronized (PublisherServiceGrpc.class) {
        if ((getPublishMethod = PublisherServiceGrpc.getPublishMethod) == null) {
          PublisherServiceGrpc.getPublishMethod = getPublishMethod = 
              io.grpc.MethodDescriptor.<org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage, org.apache.eventmesh.common.protocol.grpc.protos.Response>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(
                  "eventmesh.common.protocol.grpc.PublisherService", "publish"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.apache.eventmesh.common.protocol.grpc.protos.Response.getDefaultInstance()))
                  .setSchemaDescriptor(new PublisherServiceMethodDescriptorSupplier("publish"))
                  .build();
          }
        }
     }
     return getPublishMethod;
  }

  private static volatile io.grpc.MethodDescriptor<org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage,
      org.apache.eventmesh.common.protocol.grpc.protos.Response> getRequestReplyMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "requestReply",
      requestType = org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage.class,
      responseType = org.apache.eventmesh.common.protocol.grpc.protos.Response.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage,
      org.apache.eventmesh.common.protocol.grpc.protos.Response> getRequestReplyMethod() {
    io.grpc.MethodDescriptor<org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage, org.apache.eventmesh.common.protocol.grpc.protos.Response> getRequestReplyMethod;
    if ((getRequestReplyMethod = PublisherServiceGrpc.getRequestReplyMethod) == null) {
      synchronized (PublisherServiceGrpc.class) {
        if ((getRequestReplyMethod = PublisherServiceGrpc.getRequestReplyMethod) == null) {
          PublisherServiceGrpc.getRequestReplyMethod = getRequestReplyMethod = 
              io.grpc.MethodDescriptor.<org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage, org.apache.eventmesh.common.protocol.grpc.protos.Response>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(
                  "eventmesh.common.protocol.grpc.PublisherService", "requestReply"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.apache.eventmesh.common.protocol.grpc.protos.Response.getDefaultInstance()))
                  .setSchemaDescriptor(new PublisherServiceMethodDescriptorSupplier("requestReply"))
                  .build();
          }
        }
     }
     return getRequestReplyMethod;
  }

  private static volatile io.grpc.MethodDescriptor<org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage,
      org.apache.eventmesh.common.protocol.grpc.protos.Response> getBroadcastMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "broadcast",
      requestType = org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage.class,
      responseType = org.apache.eventmesh.common.protocol.grpc.protos.Response.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage,
      org.apache.eventmesh.common.protocol.grpc.protos.Response> getBroadcastMethod() {
    io.grpc.MethodDescriptor<org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage, org.apache.eventmesh.common.protocol.grpc.protos.Response> getBroadcastMethod;
    if ((getBroadcastMethod = PublisherServiceGrpc.getBroadcastMethod) == null) {
      synchronized (PublisherServiceGrpc.class) {
        if ((getBroadcastMethod = PublisherServiceGrpc.getBroadcastMethod) == null) {
          PublisherServiceGrpc.getBroadcastMethod = getBroadcastMethod = 
              io.grpc.MethodDescriptor.<org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage, org.apache.eventmesh.common.protocol.grpc.protos.Response>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(
                  "eventmesh.common.protocol.grpc.PublisherService", "broadcast"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.apache.eventmesh.common.protocol.grpc.protos.Response.getDefaultInstance()))
                  .setSchemaDescriptor(new PublisherServiceMethodDescriptorSupplier("broadcast"))
                  .build();
          }
        }
     }
     return getBroadcastMethod;
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
     */
    public void publish(org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage request,
        io.grpc.stub.StreamObserver<org.apache.eventmesh.common.protocol.grpc.protos.Response> responseObserver) {
      asyncUnimplementedUnaryCall(getPublishMethod(), responseObserver);
    }

    /**
     */
    public void requestReply(org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage request,
        io.grpc.stub.StreamObserver<org.apache.eventmesh.common.protocol.grpc.protos.Response> responseObserver) {
      asyncUnimplementedUnaryCall(getRequestReplyMethod(), responseObserver);
    }

    /**
     */
    public void broadcast(org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage request,
        io.grpc.stub.StreamObserver<org.apache.eventmesh.common.protocol.grpc.protos.Response> responseObserver) {
      asyncUnimplementedUnaryCall(getBroadcastMethod(), responseObserver);
    }

    @Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getPublishMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage,
                org.apache.eventmesh.common.protocol.grpc.protos.Response>(
                  this, METHODID_PUBLISH)))
          .addMethod(
            getRequestReplyMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage,
                org.apache.eventmesh.common.protocol.grpc.protos.Response>(
                  this, METHODID_REQUEST_REPLY)))
          .addMethod(
            getBroadcastMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage,
                org.apache.eventmesh.common.protocol.grpc.protos.Response>(
                  this, METHODID_BROADCAST)))
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
     */
    public void publish(org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage request,
        io.grpc.stub.StreamObserver<org.apache.eventmesh.common.protocol.grpc.protos.Response> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getPublishMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void requestReply(org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage request,
        io.grpc.stub.StreamObserver<org.apache.eventmesh.common.protocol.grpc.protos.Response> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getRequestReplyMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void broadcast(org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage request,
        io.grpc.stub.StreamObserver<org.apache.eventmesh.common.protocol.grpc.protos.Response> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getBroadcastMethod(), getCallOptions()), request, responseObserver);
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
     */
    public org.apache.eventmesh.common.protocol.grpc.protos.Response publish(org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage request) {
      return blockingUnaryCall(
          getChannel(), getPublishMethod(), getCallOptions(), request);
    }

    /**
     */
    public org.apache.eventmesh.common.protocol.grpc.protos.Response requestReply(org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage request) {
      return blockingUnaryCall(
          getChannel(), getRequestReplyMethod(), getCallOptions(), request);
    }

    /**
     */
    public org.apache.eventmesh.common.protocol.grpc.protos.Response broadcast(org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage request) {
      return blockingUnaryCall(
          getChannel(), getBroadcastMethod(), getCallOptions(), request);
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
     */
    public com.google.common.util.concurrent.ListenableFuture<org.apache.eventmesh.common.protocol.grpc.protos.Response> publish(
        org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage request) {
      return futureUnaryCall(
          getChannel().newCall(getPublishMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<org.apache.eventmesh.common.protocol.grpc.protos.Response> requestReply(
        org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage request) {
      return futureUnaryCall(
          getChannel().newCall(getRequestReplyMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<org.apache.eventmesh.common.protocol.grpc.protos.Response> broadcast(
        org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage request) {
      return futureUnaryCall(
          getChannel().newCall(getBroadcastMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_PUBLISH = 0;
  private static final int METHODID_REQUEST_REPLY = 1;
  private static final int METHODID_BROADCAST = 2;

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
          serviceImpl.publish((org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage) request,
              (io.grpc.stub.StreamObserver<org.apache.eventmesh.common.protocol.grpc.protos.Response>) responseObserver);
          break;
        case METHODID_REQUEST_REPLY:
          serviceImpl.requestReply((org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage) request,
              (io.grpc.stub.StreamObserver<org.apache.eventmesh.common.protocol.grpc.protos.Response>) responseObserver);
          break;
        case METHODID_BROADCAST:
          serviceImpl.broadcast((org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage) request,
              (io.grpc.stub.StreamObserver<org.apache.eventmesh.common.protocol.grpc.protos.Response>) responseObserver);
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
      return org.apache.eventmesh.common.protocol.grpc.protos.EventmeshGrpc.getDescriptor();
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
              .addMethod(getBroadcastMethod())
              .build();
        }
      }
    }
    return result;
  }
}
