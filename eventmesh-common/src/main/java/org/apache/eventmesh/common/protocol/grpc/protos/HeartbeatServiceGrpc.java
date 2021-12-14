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
public final class HeartbeatServiceGrpc {

  private HeartbeatServiceGrpc() {}

  public static final String SERVICE_NAME = "eventmesh.common.protocol.grpc.HeartbeatService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<org.apache.eventmesh.common.protocol.grpc.protos.Heartbeat,
      org.apache.eventmesh.common.protocol.grpc.protos.Response> getHeartbeatMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "heartbeat",
      requestType = org.apache.eventmesh.common.protocol.grpc.protos.Heartbeat.class,
      responseType = org.apache.eventmesh.common.protocol.grpc.protos.Response.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<org.apache.eventmesh.common.protocol.grpc.protos.Heartbeat,
      org.apache.eventmesh.common.protocol.grpc.protos.Response> getHeartbeatMethod() {
    io.grpc.MethodDescriptor<org.apache.eventmesh.common.protocol.grpc.protos.Heartbeat, org.apache.eventmesh.common.protocol.grpc.protos.Response> getHeartbeatMethod;
    if ((getHeartbeatMethod = HeartbeatServiceGrpc.getHeartbeatMethod) == null) {
      synchronized (HeartbeatServiceGrpc.class) {
        if ((getHeartbeatMethod = HeartbeatServiceGrpc.getHeartbeatMethod) == null) {
          HeartbeatServiceGrpc.getHeartbeatMethod = getHeartbeatMethod = 
              io.grpc.MethodDescriptor.<org.apache.eventmesh.common.protocol.grpc.protos.Heartbeat, org.apache.eventmesh.common.protocol.grpc.protos.Response>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(
                  "eventmesh.common.protocol.grpc.HeartbeatService", "heartbeat"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.apache.eventmesh.common.protocol.grpc.protos.Heartbeat.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.apache.eventmesh.common.protocol.grpc.protos.Response.getDefaultInstance()))
                  .setSchemaDescriptor(new HeartbeatServiceMethodDescriptorSupplier("heartbeat"))
                  .build();
          }
        }
     }
     return getHeartbeatMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static HeartbeatServiceStub newStub(io.grpc.Channel channel) {
    return new HeartbeatServiceStub(channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static HeartbeatServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    return new HeartbeatServiceBlockingStub(channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static HeartbeatServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    return new HeartbeatServiceFutureStub(channel);
  }

  /**
   */
  public static abstract class HeartbeatServiceImplBase implements io.grpc.BindableService {

    /**
     */
    public void heartbeat(org.apache.eventmesh.common.protocol.grpc.protos.Heartbeat request,
        io.grpc.stub.StreamObserver<org.apache.eventmesh.common.protocol.grpc.protos.Response> responseObserver) {
      asyncUnimplementedUnaryCall(getHeartbeatMethod(), responseObserver);
    }

    @Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getHeartbeatMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                org.apache.eventmesh.common.protocol.grpc.protos.Heartbeat,
                org.apache.eventmesh.common.protocol.grpc.protos.Response>(
                  this, METHODID_HEARTBEAT)))
          .build();
    }
  }

  /**
   */
  public static final class HeartbeatServiceStub extends io.grpc.stub.AbstractStub<HeartbeatServiceStub> {
    private HeartbeatServiceStub(io.grpc.Channel channel) {
      super(channel);
    }

    private HeartbeatServiceStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @Override
    protected HeartbeatServiceStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new HeartbeatServiceStub(channel, callOptions);
    }

    /**
     */
    public void heartbeat(org.apache.eventmesh.common.protocol.grpc.protos.Heartbeat request,
        io.grpc.stub.StreamObserver<org.apache.eventmesh.common.protocol.grpc.protos.Response> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getHeartbeatMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   */
  public static final class HeartbeatServiceBlockingStub extends io.grpc.stub.AbstractStub<HeartbeatServiceBlockingStub> {
    private HeartbeatServiceBlockingStub(io.grpc.Channel channel) {
      super(channel);
    }

    private HeartbeatServiceBlockingStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @Override
    protected HeartbeatServiceBlockingStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new HeartbeatServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public org.apache.eventmesh.common.protocol.grpc.protos.Response heartbeat(org.apache.eventmesh.common.protocol.grpc.protos.Heartbeat request) {
      return blockingUnaryCall(
          getChannel(), getHeartbeatMethod(), getCallOptions(), request);
    }
  }

  /**
   */
  public static final class HeartbeatServiceFutureStub extends io.grpc.stub.AbstractStub<HeartbeatServiceFutureStub> {
    private HeartbeatServiceFutureStub(io.grpc.Channel channel) {
      super(channel);
    }

    private HeartbeatServiceFutureStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @Override
    protected HeartbeatServiceFutureStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new HeartbeatServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<org.apache.eventmesh.common.protocol.grpc.protos.Response> heartbeat(
        org.apache.eventmesh.common.protocol.grpc.protos.Heartbeat request) {
      return futureUnaryCall(
          getChannel().newCall(getHeartbeatMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_HEARTBEAT = 0;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final HeartbeatServiceImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(HeartbeatServiceImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_HEARTBEAT:
          serviceImpl.heartbeat((org.apache.eventmesh.common.protocol.grpc.protos.Heartbeat) request,
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

  private static abstract class HeartbeatServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    HeartbeatServiceBaseDescriptorSupplier() {}

    @Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return org.apache.eventmesh.common.protocol.grpc.protos.EventmeshGrpc.getDescriptor();
    }

    @Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("HeartbeatService");
    }
  }

  private static final class HeartbeatServiceFileDescriptorSupplier
      extends HeartbeatServiceBaseDescriptorSupplier {
    HeartbeatServiceFileDescriptorSupplier() {}
  }

  private static final class HeartbeatServiceMethodDescriptorSupplier
      extends HeartbeatServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    HeartbeatServiceMethodDescriptorSupplier(String methodName) {
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
      synchronized (HeartbeatServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new HeartbeatServiceFileDescriptorSupplier())
              .addMethod(getHeartbeatMethod())
              .build();
        }
      }
    }
    return result;
  }
}
