package org.apache.eventmesh.runtime.rpc;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.40.0)",
    comments = "Source: event_mesh_admin_service.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class AdminBiStreamServiceGrpc {

  private AdminBiStreamServiceGrpc() {}

  public static final String SERVICE_NAME = "AdminBiStreamService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<Payload,
      Payload> getInvokeBiStreamMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "invokeBiStream",
      requestType = Payload.class,
      responseType = Payload.class,
      methodType = io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
  public static io.grpc.MethodDescriptor<Payload,
      Payload> getInvokeBiStreamMethod() {
    io.grpc.MethodDescriptor<Payload, Payload> getInvokeBiStreamMethod;
    if ((getInvokeBiStreamMethod = AdminBiStreamServiceGrpc.getInvokeBiStreamMethod) == null) {
      synchronized (AdminBiStreamServiceGrpc.class) {
        if ((getInvokeBiStreamMethod = AdminBiStreamServiceGrpc.getInvokeBiStreamMethod) == null) {
          AdminBiStreamServiceGrpc.getInvokeBiStreamMethod = getInvokeBiStreamMethod =
              io.grpc.MethodDescriptor.<Payload, Payload>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "invokeBiStream"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  Payload.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  Payload.getDefaultInstance()))
              .setSchemaDescriptor(new AdminBiStreamServiceMethodDescriptorSupplier("invokeBiStream"))
              .build();
        }
      }
    }
    return getInvokeBiStreamMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static AdminBiStreamServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<AdminBiStreamServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<AdminBiStreamServiceStub>() {
        @Override
        public AdminBiStreamServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new AdminBiStreamServiceStub(channel, callOptions);
        }
      };
    return AdminBiStreamServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static AdminBiStreamServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<AdminBiStreamServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<AdminBiStreamServiceBlockingStub>() {
        @Override
        public AdminBiStreamServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new AdminBiStreamServiceBlockingStub(channel, callOptions);
        }
      };
    return AdminBiStreamServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static AdminBiStreamServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<AdminBiStreamServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<AdminBiStreamServiceFutureStub>() {
        @Override
        public AdminBiStreamServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new AdminBiStreamServiceFutureStub(channel, callOptions);
        }
      };
    return AdminBiStreamServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public static abstract class AdminBiStreamServiceImplBase implements io.grpc.BindableService {

    /**
     */
    public io.grpc.stub.StreamObserver<Payload> invokeBiStream(
        io.grpc.stub.StreamObserver<Payload> responseObserver) {
      return io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall(getInvokeBiStreamMethod(), responseObserver);
    }

    @Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getInvokeBiStreamMethod(),
            io.grpc.stub.ServerCalls.asyncBidiStreamingCall(
              new MethodHandlers<
                Payload,
                Payload>(
                  this, METHODID_INVOKE_BI_STREAM)))
          .build();
    }
  }

  /**
   */
  public static final class AdminBiStreamServiceStub extends io.grpc.stub.AbstractAsyncStub<AdminBiStreamServiceStub> {
    private AdminBiStreamServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @Override
    protected AdminBiStreamServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new AdminBiStreamServiceStub(channel, callOptions);
    }

    /**
     */
    public io.grpc.stub.StreamObserver<Payload> invokeBiStream(
        io.grpc.stub.StreamObserver<Payload> responseObserver) {
      return io.grpc.stub.ClientCalls.asyncBidiStreamingCall(
          getChannel().newCall(getInvokeBiStreamMethod(), getCallOptions()), responseObserver);
    }
  }

  /**
   */
  public static final class AdminBiStreamServiceBlockingStub extends io.grpc.stub.AbstractBlockingStub<AdminBiStreamServiceBlockingStub> {
    private AdminBiStreamServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @Override
    protected AdminBiStreamServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new AdminBiStreamServiceBlockingStub(channel, callOptions);
    }
  }

  /**
   */
  public static final class AdminBiStreamServiceFutureStub extends io.grpc.stub.AbstractFutureStub<AdminBiStreamServiceFutureStub> {
    private AdminBiStreamServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @Override
    protected AdminBiStreamServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new AdminBiStreamServiceFutureStub(channel, callOptions);
    }
  }

  private static final int METHODID_INVOKE_BI_STREAM = 0;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AdminBiStreamServiceImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(AdminBiStreamServiceImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }

    @Override
    @SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_INVOKE_BI_STREAM:
          return (io.grpc.stub.StreamObserver<Req>) serviceImpl.invokeBiStream(
              (io.grpc.stub.StreamObserver<Payload>) responseObserver);
        default:
          throw new AssertionError();
      }
    }
  }

  private static abstract class AdminBiStreamServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    AdminBiStreamServiceBaseDescriptorSupplier() {}

    @Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return EventMeshAdminService.getDescriptor();
    }

    @Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("AdminBiStreamService");
    }
  }

  private static final class AdminBiStreamServiceFileDescriptorSupplier
      extends AdminBiStreamServiceBaseDescriptorSupplier {
    AdminBiStreamServiceFileDescriptorSupplier() {}
  }

  private static final class AdminBiStreamServiceMethodDescriptorSupplier
      extends AdminBiStreamServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    AdminBiStreamServiceMethodDescriptorSupplier(String methodName) {
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
      synchronized (AdminBiStreamServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new AdminBiStreamServiceFileDescriptorSupplier())
              .addMethod(getInvokeBiStreamMethod())
              .build();
        }
      }
    }
    return result;
  }
}
