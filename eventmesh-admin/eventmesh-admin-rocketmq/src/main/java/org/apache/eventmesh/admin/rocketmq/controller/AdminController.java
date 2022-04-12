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

package org.apache.eventmesh.admin.rocketmq.controller;

import static org.apache.eventmesh.admin.rocketmq.Constants.TOPIC_MANAGE_PATH;

import org.apache.eventmesh.admin.rocketmq.handler.TopicsHandler;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpServer;

public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    public AdminController() {
    }

    public void run(HttpServer server) throws IOException {

        server.createContext(TOPIC_MANAGE_PATH, new TopicsHandler());

        logger.info("EventMesh-Admin Controller server context created successfully");
    }
}
