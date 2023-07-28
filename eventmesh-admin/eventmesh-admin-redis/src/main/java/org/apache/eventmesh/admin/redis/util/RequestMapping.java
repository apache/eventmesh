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

package org.apache.eventmesh.admin.redis.util;

import org.apache.eventmesh.common.enums.HttpMethod;

import com.sun.net.httpserver.HttpExchange;

import lombok.experimental.UtilityClass;

/**
 * This class provides utility methods for handling HTTP request mappings.
 */

@UtilityClass
public class RequestMapping {

    /**
     * Performs a POST mapping for a specific URL.
     *
     * @param value        the URL pattern to match
     * @param httpExchange the exchange containing the request from the client and used to send the response
     * @return {@code true} if the URL pattern matches and the HTTP method is POST, {@code false} otherwise
     */
    public boolean postMapping(String value, HttpExchange httpExchange) {
        return isUrlMatch(value, httpExchange, HttpMethod.POST.name());
    }

    /**
     * Performs a GET mapping for a specific URL.
     *
     * @param value        the URL pattern to match
     * @param httpExchange the exchange containing the request from the client and used to send the response
     * @return {@code true} if the URL pattern matches and the HTTP method is GET, {@code false} otherwise
     */
    public boolean getMapping(String value, HttpExchange httpExchange) {
        return isUrlMatch(value, httpExchange, HttpMethod.GET.name());
    }

    /**
     * Performs a PUT mapping for a specific URL.
     *
     * @param value        the URL pattern to match
     * @param httpExchange the exchange containing the request from the client and used to send the response
     * @return {@code true} if the URL pattern matches and the HTTP method is PUT, {@code false} otherwise
     */
    public boolean putMapping(String value, HttpExchange httpExchange) {
        return isUrlMatch(value, httpExchange, HttpMethod.PUT.name());
    }

    /**
     * Performs a DELETE mapping for a specific URL.
     *
     * @param value        the URL pattern to match
     * @param httpExchange the exchange containing the request from the client and used to send the response
     * @return {@code true} if the URL pattern matches and the HTTP method is DELETE, {@code false} otherwise
     */
    public boolean deleteMapping(String value, HttpExchange httpExchange) {
        return isUrlMatch(value, httpExchange, HttpMethod.DELETE.name());
    }

    /**
     * Checks if the URL pattern matches the request URI and the HTTP method is the specified method type.
     *
     * @param value        the URL pattern to match
     * @param httpExchange the exchange containing the request from the client and used to send the response
     * @param methodType   the HTTP method type to check against
     * @return {@code true} if the URL pattern matches and the HTTP method is the specified method type, {@code false} otherwise
     */
    private boolean isUrlMatch(String value, HttpExchange httpExchange, String methodType) {
        if (methodType.equalsIgnoreCase(httpExchange.getRequestMethod())) {
            String requestUri = httpExchange.getRequestURI().getPath();
            UrlMappingPattern matcher = new UrlMappingPattern(value);
            return matcher.matches(requestUri);
        }
        return false;
    }

}
