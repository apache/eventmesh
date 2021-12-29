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

import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.http.Consts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;

public class NetUtils {

    private static final Logger logger = LoggerFactory.getLogger(NetUtils.class);

    public static String parsePostBody(HttpExchange exchange)
            throws IOException {
        StringBuilder body = new StringBuilder();
        if ("post".equalsIgnoreCase(exchange.getRequestMethod())
                || "put".equalsIgnoreCase(exchange.getRequestMethod())) {
            try (InputStreamReader reader =
                         new InputStreamReader(exchange.getRequestBody(), Consts.UTF_8)) {
                char[] buffer = new char[256];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    body.append(buffer, 0, read);
                }
            }
        }
        return body.toString();
    }
}

