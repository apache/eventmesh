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

package org.apache.eventmesh.server.tcp.admin.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.server.tcp.EventMeshTCPServer;
import org.apache.eventmesh.server.tcp.config.TcpProtocolConstants;
import org.apache.eventmesh.server.tcp.rebalance.recommend.EventMeshRecommendImpl;
import org.apache.eventmesh.server.tcp.rebalance.recommend.EventMeshRecommendStrategy;
import org.apache.eventmesh.server.tcp.utils.NetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

/**
 * query recommend eventmesh
 */
public class QueryRecommendEventMeshHandler implements HttpHandler {

    private Logger logger = LoggerFactory.getLogger(QueryRecommendEventMeshHandler.class);

    private final EventMeshTCPServer eventMeshTCPServer;

    public QueryRecommendEventMeshHandler(EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        String result = "";
        OutputStream out = httpExchange.getResponseBody();
        try{
            if(!CommonConfiguration.eventMeshServerRegistryEnable) {
                throw new Exception("registry enable config is false, not support");
            }
            String queryString =  httpExchange.getRequestURI().getQuery();
            Map<String,String> queryStringInfo = NetUtils.formData2Dic(queryString);
            String group = queryStringInfo.get(TcpProtocolConstants.MANAGE_GROUP);
            String purpose = queryStringInfo.get(TcpProtocolConstants.MANAGE_PURPOSE);
            if (StringUtils.isBlank(group) || StringUtils.isBlank(purpose)) {
                httpExchange.sendResponseHeaders(200, 0);
                result = "params illegal!";
                out.write(result.getBytes());
                return;
            }

            EventMeshRecommendStrategy eventMeshRecommendStrategy = new EventMeshRecommendImpl(eventMeshTCPServer);
            String recommendEventMeshResult = eventMeshRecommendStrategy.calculateRecommendEventMesh(group, purpose);
            result = (recommendEventMeshResult == null) ? "null" : recommendEventMeshResult;
            logger.info("recommend eventmesh:{},group:{},purpose:{}",result, group, purpose);
            httpExchange.sendResponseHeaders(200, 0);
            out.write(result.getBytes());
        }catch (Exception e){
            logger.error("QueryRecommendEventMeshHandler fail...", e);
        }finally {
            if(out != null){
                try {
                    out.close();
                }catch (IOException e){
                    logger.warn("out close failed...", e);
                }
            }
        }
    }
}
