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

package org.apache.eventmesh.runtime.admin.handler;

import org.apache.eventmesh.api.registry.dto.EventMeshDataInfo;
import org.apache.eventmesh.runtime.admin.response.Error;
import org.apache.eventmesh.runtime.admin.response.GetRegistryResponse;
import org.apache.eventmesh.runtime.admin.utils.JsonUtils;
import org.apache.eventmesh.runtime.registry.Registry;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class RegistryHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationHandler.class);
    private final Registry eventMeshRegistry;

    public RegistryHandler(Registry eventMeshRegistry) {
        this.eventMeshRegistry = eventMeshRegistry;
    }

    /**
     * OPTION /registry
     */
    void preflight(HttpExchange httpExchange) throws IOException {
        httpExchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        httpExchange.getResponseHeaders().add("Access-Control-Allow-Method", "*");
        httpExchange.getResponseHeaders().add("Access-Control-Max-Age", "86400");
        httpExchange.sendResponseHeaders(200, 0);
        OutputStream out = httpExchange.getResponseBody();
        out.close();
    }

    /**
     * GET /registry
     * Return a response that contains the list of EventMesh clusters
     */
    void get(HttpExchange httpExchange) throws IOException {
        OutputStream out = httpExchange.getResponseBody();
        httpExchange.getResponseHeaders().add("Content-Type", "application/json");
        httpExchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

        try {
            List<GetRegistryResponse> getRegistryResponseList = new ArrayList<>();
            List<EventMeshDataInfo> eventMeshDataInfos = eventMeshRegistry.findAllEventMeshInfo();
            for (EventMeshDataInfo eventMeshDataInfo : eventMeshDataInfos) {
                GetRegistryResponse getRegistryResponse = new GetRegistryResponse(
                    eventMeshDataInfo.getEventMeshClusterName(),
                    eventMeshDataInfo.getEventMeshName(),
                    eventMeshDataInfo.getEndpoint(),
                    eventMeshDataInfo.getLastUpdateTimestamp(),
                    eventMeshDataInfo.getMetadata().toString()
                );
                getRegistryResponseList.add(getRegistryResponse);
            }
            getRegistryResponseList.sort(Comparator.comparing(lhs -> lhs.eventMeshClusterName));

            String result = JsonUtils.toJson(getRegistryResponseList);
            httpExchange.sendResponseHeaders(200, result.getBytes().length);
            out.write(result.getBytes());
        } catch (NullPointerException e) {
            String result = JsonUtils.toJson(new ArrayList<>());
            httpExchange.sendResponseHeaders(200, result.getBytes().length);
            out.write(result.getBytes());
        } catch (Exception e) {
            StringWriter writer = new StringWriter();
            PrintWriter printWriter = new PrintWriter(writer);
            e.printStackTrace(printWriter);
            printWriter.flush();
            String stackTrace = writer.toString();

            Error error = new Error(e.toString(), stackTrace);
            String result = JsonUtils.toJson(error);
            httpExchange.sendResponseHeaders(500, result.getBytes().length);
            out.write(result.getBytes());
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    logger.warn("out close failed...", e);
                }
            }
        }
    }


    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        if (httpExchange.getRequestMethod().equals("OPTION")) {
            preflight(httpExchange);
        }
        if (httpExchange.getRequestMethod().equals("GET")) {
            get(httpExchange);
        }
    }
}
