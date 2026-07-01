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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

/**
 * HttpResponseUtils test cases.
 */
public class HttpResponseUtilsTest {

    @Test
    public void testCreateSuccess() {
        Assertions.assertEquals(HttpVersion.HTTP_1_1, HttpResponseUtils.createSuccess().protocolVersion());
        Assertions.assertEquals(HttpResponseStatus.OK, HttpResponseUtils.createSuccess().status());
    }

    @Test
    public void testCreateNotFound() {
        Assertions.assertEquals(HttpVersion.HTTP_1_1, HttpResponseUtils.createNotFound().protocolVersion());
        Assertions.assertEquals(HttpResponseStatus.NOT_FOUND, HttpResponseUtils.createNotFound().status());
    }

    @Test
    public void testCreateInternalServerError() {
        Assertions.assertEquals(HttpVersion.HTTP_1_1, HttpResponseUtils.createInternalServerError().protocolVersion());
        Assertions.assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR, HttpResponseUtils.createInternalServerError().status());
    }

}
