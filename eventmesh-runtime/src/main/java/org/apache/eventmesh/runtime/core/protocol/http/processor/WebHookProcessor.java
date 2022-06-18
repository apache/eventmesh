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

package org.apache.eventmesh.runtime.core.protocol.http.processor;

import io.netty.handler.codec.http.*;
import org.apache.eventmesh.webhook.receive.WebHookController;

import java.util.HashMap;
import java.util.Map;

public class WebHookProcessor implements HttpProcessor {

    private WebHookController webHookController;

    @Override
    public String[] paths() {

        return new String[]{"/webhook"};
    }

    @Override
    public HttpResponse handler(HttpRequest httpRequest) {
        try {
            Map<String, String> header = new HashMap<>();
            for (Map.Entry<String, String> entry : httpRequest.headers().entries()) {
                header.put(entry.getKey(), entry.getValue());
            }
            byte[] bytes = ((DefaultFullHttpRequest) httpRequest).content().array();
            webHookController.execute(httpRequest.uri(), header, bytes);
            return new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        } catch (Exception e) {
            return new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
