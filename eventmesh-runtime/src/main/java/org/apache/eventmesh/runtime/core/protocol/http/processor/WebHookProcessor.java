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

import org.apache.eventmesh.runtime.common.EventMeshTrace;
import org.apache.eventmesh.runtime.util.HttpResponseUtils;
import org.apache.eventmesh.webhook.receive.WebHookController;

import java.util.HashMap;
import java.util.Map;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

import lombok.Setter;

@EventMeshTrace(isEnable = true)
public class WebHookProcessor implements HttpProcessor {

    @Setter
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
                header.put(entry.getKey().toLowerCase(), entry.getValue());
            }
            FullHttpRequest fullHttpRequest = ((FullHttpRequest) httpRequest);
            int length = fullHttpRequest.content().readableBytes();
            if (length > 0) {
                byte[] body = new byte[length];
                fullHttpRequest.content().readBytes(body);
                webHookController.execute(httpRequest.uri(), header, body);
                return HttpResponseUtils.createSuccess();
            }
            throw new RuntimeException("Body has no data ");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
