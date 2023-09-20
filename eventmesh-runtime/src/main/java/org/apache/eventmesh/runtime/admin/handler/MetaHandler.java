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

import org.apache.eventmesh.api.meta.dto.EventMeshDataInfo;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.enums.HttpMethod;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.admin.controller.HttpHandlerManager;
import org.apache.eventmesh.runtime.admin.response.Error;
import org.apache.eventmesh.runtime.admin.response.GetRegistryResponse;
import org.apache.eventmesh.runtime.common.EventHttpHandler;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.meta.MetaStorage;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.sun.net.httpserver.HttpExchange;

import lombok.extern.slf4j.Slf4j;

/**
 * This class handles the {@code /registry} endpoint,
 * corresponding to the {@code eventmesh-dashboard} path {@code /registry}.
 * <p>
 * This handler is responsible for retrieving a list of EventMesh clusters from the
 * {@link MetaStorage} object, encapsulate them into a list of {@link GetRegistryResponse} objects,
 * and sort them by {@code EventMeshClusterName}.
 *
 * @see AbstractHttpHandler
 */

@Slf4j
@EventHttpHandler(path = "/meta")
public class MetaHandler extends AbstractHttpHandler {

    private final MetaStorage eventMeshMetaStorage;

    /**
     * Constructs a new instance with the specified {@link MetaStorage} and {@link HttpHandlerManager}.
     *
     * @param eventMeshMetaStorage  The {@link MetaStorage} instance used for retrieving EventMesh cluster information.
     * @param httpHandlerManager Manages the registration of {@linkplain com.sun.net.httpserver.HttpHandler HttpHandler}
     *                           for an {@linkplain com.sun.net.httpserver.HttpServer HttpServer}.
     */
    public MetaHandler(MetaStorage eventMeshMetaStorage,
                       HttpHandlerManager httpHandlerManager) {
        super(httpHandlerManager);
        this.eventMeshMetaStorage = eventMeshMetaStorage;
    }

    /**
     * Handles the OPTIONS request first for {@code /registry}.
     * <p>
     * This method adds CORS (Cross-Origin Resource Sharing) response headers to
     * the {@link HttpExchange} object and sends a 200 status code.
     *
     * @param httpExchange the exchange containing the request from the client and used to send the response
     * @throws IOException if an I/O error occurs while handling the request
     */
    void preflight(HttpExchange httpExchange) throws IOException {
        httpExchange.getResponseHeaders().add(EventMeshConstants.HANDLER_ORIGIN, "*");
        httpExchange.getResponseHeaders().add(EventMeshConstants.HANDLER_METHODS, "*");
        httpExchange.getResponseHeaders().add(EventMeshConstants.HANDLER_AGE, EventMeshConstants.MAX_AGE);
        httpExchange.sendResponseHeaders(200, 0);
        OutputStream out = httpExchange.getResponseBody();
        out.close();
    }

    /**
     * Handles the GET request for {@code /registry}.
     * <p>
     * This method retrieves a list of EventMesh clusters and returns it as a JSON response.
     *
     * @param httpExchange the exchange containing the request from the client and used to send the response
     * @throws IOException if an I/O error occurs while handling the request
     */
    void get(HttpExchange httpExchange) throws IOException {
        OutputStream out = httpExchange.getResponseBody();
        httpExchange.getResponseHeaders().add(EventMeshConstants.CONTENT_TYPE, EventMeshConstants.APPLICATION_JSON);
        httpExchange.getResponseHeaders().add(EventMeshConstants.HANDLER_ORIGIN, "*");

        try {
            List<GetRegistryResponse> getRegistryResponseList = new ArrayList<>();
            List<EventMeshDataInfo> eventMeshDataInfos = eventMeshMetaStorage.findAllEventMeshInfo();
            for (EventMeshDataInfo eventMeshDataInfo : eventMeshDataInfos) {
                GetRegistryResponse getRegistryResponse = new GetRegistryResponse(
                    eventMeshDataInfo.getEventMeshClusterName(),
                    eventMeshDataInfo.getEventMeshName(),
                    eventMeshDataInfo.getEndpoint(),
                    eventMeshDataInfo.getLastUpdateTimestamp(),
                    eventMeshDataInfo.getMetadata().toString());
                getRegistryResponseList.add(getRegistryResponse);
            }
            getRegistryResponseList.sort(Comparator.comparing(GetRegistryResponse::getEventMeshClusterName));

            String result = JsonUtils.toJSONString(getRegistryResponseList);
            httpExchange.sendResponseHeaders(200, Objects.requireNonNull(result).getBytes(Constants.DEFAULT_CHARSET).length);
            out.write(result.getBytes(Constants.DEFAULT_CHARSET));
        } catch (NullPointerException e) {
            // registry not initialized, return empty list
            String result = JsonUtils.toJSONString(new ArrayList<>());
            httpExchange.sendResponseHeaders(200, Objects.requireNonNull(result).getBytes(Constants.DEFAULT_CHARSET).length);
            out.write(result.getBytes(Constants.DEFAULT_CHARSET));
        } catch (Exception e) {
            StringWriter writer = new StringWriter();
            PrintWriter printWriter = new PrintWriter(writer);
            e.printStackTrace(printWriter);
            printWriter.flush();
            String stackTrace = writer.toString();

            Error error = new Error(e.toString(), stackTrace);
            String result = JsonUtils.toJSONString(error);
            httpExchange.sendResponseHeaders(500, Objects.requireNonNull(result).getBytes(Constants.DEFAULT_CHARSET).length);
            out.write(result.getBytes(Constants.DEFAULT_CHARSET));
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    log.warn("out close failed...", e);
                }
            }
        }
    }

    /**
     * Handles the HTTP requests for {@code /registry}.
     * <p>
     * It delegates the handling to {@code preflight()} or {@code get()} methods
     * based on the request method type (OPTIONS or GET).
     * <p>
     * This method is an implementation of {@linkplain com.sun.net.httpserver.HttpHandler#handle(HttpExchange)  HttpHandler.handle()}
     *
     * @param httpExchange the exchange containing the request from the client and used to send the response
     * @throws IOException if an I/O error occurs while handling the request
     */
    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        switch (HttpMethod.valueOf(httpExchange.getRequestMethod())) {
            case OPTIONS:
                preflight(httpExchange);
                break;
            case GET:
                get(httpExchange);
                break;
            default:
                break;
        }
    }
}
