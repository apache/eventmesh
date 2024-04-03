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

package org.apache.eventmesh.runtime.util;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;

public class HttpRequestUtil {

    private static final DefaultHttpDataFactory DEFAULT_HTTP_DATA_FACTORY = new DefaultHttpDataFactory(false);

    public static <T> Map<String, Object> parseHttpRequestBody(final HttpRequest httpRequest, @Nullable Supplier<T> start, @Nullable Consumer<T> end)
        throws IOException {
        T t = null;
        if (!Objects.isNull(start)) {
            t = start.get();
        }
        final Map<String, Object> httpRequestBody = new HashMap<>();
        if (io.netty.handler.codec.http.HttpMethod.GET.equals(httpRequest.method())) {
            new QueryStringDecoder(httpRequest.uri())
                .parameters()
                .forEach((key, value) -> httpRequestBody.put(key, value.get(0)));
        } else if (io.netty.handler.codec.http.HttpMethod.POST.equals(httpRequest.method())) {
            decodeHttpRequestBody(httpRequest, httpRequestBody);
        }
        if (Objects.isNull(t)) {
            end.accept(t);
        }
        return httpRequestBody;
    }

    private static void decodeHttpRequestBody(HttpRequest httpRequest, Map<String, Object> httpRequestBody) throws IOException {
        final HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(DEFAULT_HTTP_DATA_FACTORY, httpRequest);
        for (final InterfaceHttpData param : decoder.getBodyHttpDatas()) {
            if (InterfaceHttpData.HttpDataType.Attribute == param.getHttpDataType()) {
                final Attribute data = (Attribute) param;
                httpRequestBody.put(data.getName(), data.getValue());
            }
        }
        decoder.destroy();
    }

}
