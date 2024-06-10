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

package org.apache.eventmesh.common.protocol.workflow.protos;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.64.0)",
    comments = "Source: workflow.proto")
@io.grpc.stub.annotations.GrpcGenerated
@SuppressWarnings({"all"})
public final class WorkflowGrpc {

  private WorkflowGrpc() {}

  public static final String SERVICE_NAME = "eventmesh.workflow.api.protocol.Workflow";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<ExecuteRequest,
      ExecuteResponse> getExecuteMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Execute",
      requestType = ExecuteRequest.class,
      responseType = ExecuteResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<ExecuteRequest,
      ExecuteResponse> getExecuteMethod() {
    io.grpc.MethodDescriptor<ExecuteRequest, ExecuteResponse> getExecuteMethod;
    if ((getExecuteMethod = WorkflowGrpc.getExecuteMethod) == null) {
      synchronized (WorkflowGrpc.class) {
        if ((getExecuteMethod = WorkflowGrpc.getExecuteMethod) == null) {
          WorkflowGrpc.getExecuteMethod = getExecuteMethod =
              io.grpc.MethodDescriptor.<ExecuteRequest, ExecuteResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Execute"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ExecuteRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  ExecuteResponse.getDefaultInstance()))
              .setSchemaDescriptor(new WorkflowMethodDescriptorSupplier("Execute"))
              .build();
        }
      }
    }
    return getExecuteMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static WorkflowStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<WorkflowStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<WorkflowStub>() {
        @Override
        public WorkflowStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new WorkflowStub(channel, callOptions);
        }
      };
    return WorkflowStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static WorkflowBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<WorkflowBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<WorkflowBlockingStub>() {
        @Override
        public WorkflowBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new WorkflowBlockingStub(channel, callOptions);
        }
      };
    return WorkflowBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static WorkflowFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<WorkflowFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<WorkflowFutureStub>() {
        @Override
        public WorkflowFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new WorkflowFutureStub(channel, callOptions);
        }
      };
    return WorkflowFutureStub.newStub(factory, channel);
  }

  /**
   */
  public static abstract class WorkflowImplBase implements io.grpc.BindableService {

    /**
     */
    public void execute(ExecuteRequest request,
                        io.grpc.stub.StreamObserver<ExecuteResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getExecuteMethod(), responseObserver);
    }

    @Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getExecuteMethod(),
            io.grpc.stub.ServerCalls.asyncUnaryCall(
              new MethodHandlers<
                ExecuteRequest,
                ExecuteResponse>(
                  this, METHODID_EXECUTE)))
          .build();
    }
  }

  /**
   */
  public static final class WorkflowStub extends io.grpc.stub.AbstractAsyncStub<WorkflowStub> {
    private WorkflowStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @Override
    protected WorkflowStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new WorkflowStub(channel, callOptions);
    }

    /**
     */
    public void execute(ExecuteRequest request,
                        io.grpc.stub.StreamObserver<ExecuteResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getExecuteMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   */
  public static final class WorkflowBlockingStub extends io.grpc.stub.AbstractBlockingStub<WorkflowBlockingStub> {
    private WorkflowBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @Override
    protected WorkflowBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new WorkflowBlockingStub(channel, callOptions);
    }

    /**
     */
    public ExecuteResponse execute(ExecuteRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getExecuteMethod(), getCallOptions(), request);
    }
  }

  /**
   */
  public static final class WorkflowFutureStub extends io.grpc.stub.AbstractFutureStub<WorkflowFutureStub> {
    private WorkflowFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @Override
    protected WorkflowFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new WorkflowFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<ExecuteResponse> execute(
        ExecuteRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getExecuteMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_EXECUTE = 0;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final WorkflowImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(WorkflowImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_EXECUTE:
          serviceImpl.execute((ExecuteRequest) request,
              (io.grpc.stub.StreamObserver<ExecuteResponse>) responseObserver);
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

  private static abstract class WorkflowBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    WorkflowBaseDescriptorSupplier() {}

    @Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return EventmeshWorkflowGrpc.getDescriptor();
    }

    @Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("Workflow");
    }
  }

  private static final class WorkflowFileDescriptorSupplier
      extends WorkflowBaseDescriptorSupplier {
    WorkflowFileDescriptorSupplier() {}
  }

  private static final class WorkflowMethodDescriptorSupplier
      extends WorkflowBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    WorkflowMethodDescriptorSupplier(String methodName) {
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
      synchronized (WorkflowGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new WorkflowFileDescriptorSupplier())
              .addMethod(getExecuteMethod())
              .build();
        }
      }
    }
    return result;
  }
}
