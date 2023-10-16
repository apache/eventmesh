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

package org.apache.eventmesh.admin.controller;

import org.apache.eventmesh.admin.service.ConnectionService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class ConnectionController {

    @Autowired
    public ConnectionService connectionService;

    /**
     * Query Connection List
     * <p>
     * The subscription information of SourceConnector and SinkConnector reported by EventMesh Runtime to the meta,
     * containing the configuration of each connection.
     *
     * @param page the page number
     * @param size the page size
     * @return A paged list of connection configuration and total number of pages
     */
    @GetMapping("/connection")
    public String listConnections(@RequestParam("page") Integer page, @RequestParam("size") String size) {
        return null;
    }

}
