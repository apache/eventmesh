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

package org.apache.eventmesh.admin.api.util;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.eventmesh.common.enums.HttpMethod;

import java.net.URI;
import java.net.URISyntaxException;

import org.junit.Test;

import com.sun.net.httpserver.HttpExchange;

public class RequestMappingTest {

    @Test
    public void testPostMapping() throws URISyntaxException {
        HttpExchange httpExchange = mock(HttpExchange.class);
        when(httpExchange.getRequestMethod()).thenReturn(HttpMethod.POST.name());
        URI requestUri = new URI("/test/123");
        when(httpExchange.getRequestURI()).thenReturn(requestUri);

        boolean result = RequestMapping.postMapping("/test/{value}", httpExchange);
        assertTrue(result);
    }

    @Test
    public void testGetMapping() throws URISyntaxException {
        HttpExchange httpExchange = mock(HttpExchange.class);
        when(httpExchange.getRequestMethod()).thenReturn(HttpMethod.GET.name());
        URI requestUri = new URI("/test/123");
        when(httpExchange.getRequestURI()).thenReturn(requestUri);

        boolean result = RequestMapping.getMapping("/test/{value}", httpExchange);
        assertTrue(result);
    }

    @Test
    public void testPutMapping() throws URISyntaxException {
        HttpExchange httpExchange = mock(HttpExchange.class);
        when(httpExchange.getRequestMethod()).thenReturn(HttpMethod.PUT.name());
        URI requestUri = new URI("/test/123");
        when(httpExchange.getRequestURI()).thenReturn(requestUri);

        boolean result = RequestMapping.putMapping("/test/{value}", httpExchange);
        assertTrue(result);
    }

    @Test
    public void testDeleteMapping() throws URISyntaxException {
        HttpExchange httpExchange = mock(HttpExchange.class);
        when(httpExchange.getRequestMethod()).thenReturn(HttpMethod.DELETE.name());
        URI requestUri = new URI("/test/123");
        when(httpExchange.getRequestURI()).thenReturn(requestUri);

        boolean result = RequestMapping.deleteMapping("/test/{value}", httpExchange);
        assertTrue(result);
    }
}
