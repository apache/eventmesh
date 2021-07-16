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
import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.core.plugin.PluginFactory;
import org.apache.eventmesh.runtime.util.JsonUtils;
import org.apache.eventmesh.runtime.util.NetUtils;
import org.apache.eventmesh.store.api.openschema.common.ServiceException;
import org.apache.eventmesh.store.api.openschema.request.SubjectCreateRequest;
import org.apache.eventmesh.store.api.openschema.response.SubjectResponse;
import org.apache.eventmesh.store.api.openschema.service.SchemaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class CreateSubjectHandler implements HttpHandler {
	
	private Logger logger = LoggerFactory.getLogger(CreateSubjectHandler.class);
	
	private final EventMeshTCPServer eventMeshTCPServer;

    public CreateSubjectHandler(EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
    }
	
    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        String result = "";        
        OutputStream out = httpExchange.getResponseBody();
        try {
            String payload = NetUtils.parsePostBody(httpExchange);                                
            SubjectCreateRequest subjectCreateRequest = JsonUtils.deserialize(SubjectCreateRequest.class, payload);            		               
            String subject = subjectCreateRequest.getSubject();
            
            if (StringUtils.isBlank(subject)) {
                result = "Create subject failed. Parameter subject not found.";
                logger.error(result);
                out.write(result.getBytes());
                return;
            }
            SchemaService schemaService = PluginFactory.getSchemaService(eventMeshTCPServer.getEventMeshTCPConfiguration().eventMeshStorePluginSchemaService);
            if (schemaService == null) {
                logger.error("can't load the schemaService plugin, please check.");
                throw new RuntimeException("doesn't load the schemaService plugin, please check.");
            }
             
            SubjectResponse subjectResponse = schemaService.createSubject(subjectCreateRequest);
            if (subjectResponse != null) {
                logger.info("createTopic subject: {}", subject);                      
                httpExchange.getResponseHeaders().add("Content-Type", "appication/json");
                httpExchange.sendResponseHeaders(200, 0);
                result = JsonUtils.toJson(subjectResponse);                
                logger.info(result);
                out.write(result.getBytes());
                return;
            } else {
                httpExchange.sendResponseHeaders(500, 0);
                result = String.format("create subject failed! Server side error");
                logger.error(result);
                out.write(result.getBytes());
                return;
            }
        } catch (ServiceException e) {            	
        	httpExchange.getResponseHeaders().add("Content-Type", "appication/json");
            httpExchange.sendResponseHeaders(500, 0);                            
            result = JsonUtils.toJson(e.getErrorResponse());
            logger.error(result);
            out.write(result.getBytes());
            return;
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
}