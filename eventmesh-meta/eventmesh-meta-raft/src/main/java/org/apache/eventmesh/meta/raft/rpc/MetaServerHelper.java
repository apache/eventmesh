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

package org.apache.eventmesh.meta.raft.rpc;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import com.alipay.sofa.jraft.rpc.RpcServer;
import com.alipay.sofa.jraft.util.RpcFactoryHelper;
import com.google.protobuf.Message;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MetaServerHelper {

    public static RpcServer rpcServer;


    public static void setRpcServer(RpcServer rpcServer) {
        MetaServerHelper.rpcServer = rpcServer;
    }


    public static void initGRpc() {
        if ("com.alipay.sofa.jraft.rpc.impl.GrpcRaftRpcFactory".equals(
            RpcFactoryHelper.rpcFactory().getClass().getName())) {
            RpcFactoryHelper.rpcFactory()
                .registerProtobufSerializer(RequestResponse.class.getName(),
                    RequestResponse.getDefaultInstance());
            try {
                Class<?> clazz = Class.forName("com.alipay.sofa.jraft.rpc.impl.MarshallerHelper");
                Method registerRespInstance = clazz.getMethod("registerRespInstance", String.class, Message.class);
                registerRespInstance.invoke(null, RequestResponse.class.getName(),
                    RequestResponse.getDefaultInstance());
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    public static void shutDown() {
        if (rpcServer == null) {
            return;
        }
        rpcServer.shutdown();
    }

    public static void blockUntilShutdown() {
        if (rpcServer == null) {
            return;
        }
        if ("com.alipay.sofa.jraft.rpc.impl.GrpcRaftRpcFactory".equals(
            RpcFactoryHelper.rpcFactory().getClass().getName())) {
            try {
                Method getServer = rpcServer.getClass().getMethod("getServer");
                Object grpcServer = getServer.invoke(rpcServer);

                Method shutdown = grpcServer.getClass().getMethod("shutdown");
                Method awaitTerminationLimit = grpcServer.getClass()
                    .getMethod("awaitTermination", long.class, TimeUnit.class);

                Runtime.getRuntime().addShutdownHook(new Thread() {
                    @Override
                    public void run() {
                        try {
                            shutdown.invoke(grpcServer);
                            awaitTerminationLimit.invoke(grpcServer, 30, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                            log.error(e.getMessage(), e);
                        }
                    }
                });
                Method awaitTermination = grpcServer.getClass().getMethod("awaitTermination");
                awaitTermination.invoke(grpcServer);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
    }
}
