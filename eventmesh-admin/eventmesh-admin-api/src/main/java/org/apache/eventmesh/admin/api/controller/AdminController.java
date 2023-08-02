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

package org.apache.eventmesh.admin.api.controller;

import static org.apache.eventmesh.admin.api.Constants.TOPIC_MANAGE_PATH;

import org.apache.eventmesh.admin.api.handler.TopicHandler;

import java.io.IOException;

import com.sun.net.httpserver.HttpServer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AdminController {

    public AdminController() {
    }

    /**
     * Invoke this method to run the admin module.
     *
     * @param server A HttpServer is bound to an IP address and port number
     *               and listens for incoming TCP connections from clients on this address.
     * @throws IOException
     * @see HttpServer
     */
    public void run(HttpServer server) throws IOException {

        // Creates a mapping from API URI path to the exchange handler on this HttpServer.
        server.createContext(TOPIC_MANAGE_PATH, new TopicHandler());

        log.info("EventMesh-Admin Controller server context created successfully");
    }
}
