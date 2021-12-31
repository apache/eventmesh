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

package org.apache.eventmesh.admin.rocketmq.util;

import com.sun.net.httpserver.HttpExchange;

public class RequestMapping {

    public static boolean postMapping(String value, HttpExchange httpExchange) {
        if ("post".equalsIgnoreCase(httpExchange.getRequestMethod())) {
            String requestUri = httpExchange.getRequestURI().getPath();
            UrlMappingPattern matcher = new UrlMappingPattern(value);
            return matcher.matches(requestUri);
        }
        return false;
    }

    public static boolean getMapping(String value, HttpExchange httpExchange) {
        if ("get".equalsIgnoreCase(httpExchange.getRequestMethod())) {
            String requestUri = httpExchange.getRequestURI().getPath();
            UrlMappingPattern matcher = new UrlMappingPattern(value);
            return matcher.matches(requestUri);
        }
        return false;
    }

    public static boolean putMapping(String value, HttpExchange httpExchange) {
        if ("put".equalsIgnoreCase(httpExchange.getRequestMethod())) {
            String requestUri = httpExchange.getRequestURI().getPath();
            UrlMappingPattern matcher = new UrlMappingPattern(value);
            return matcher.matches(requestUri);
        }
        return false;
    }

    public static boolean deleteMapping(String value, HttpExchange httpExchange) {
        if ("delete".equalsIgnoreCase(httpExchange.getRequestMethod())) {
            String requestUri = httpExchange.getRequestURI().getPath();
            UrlMappingPattern matcher = new UrlMappingPattern(value);
            return matcher.matches(requestUri);
        }
        return false;
    }

}
