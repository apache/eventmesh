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

import io.grpc.Server;
import io.grpc.ServerBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CloudEventsSubscribeInstance {

    public static void main(String[] args) throws Exception {
        Server server = ServerBuilder.forPort(10115).addService(new CallbackService()).build();
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.shutdown();
            } catch (Exception e) {
                log.error("exception when shutdown.", e);
            }
        }));
        server.awaitTermination();
    }
}
