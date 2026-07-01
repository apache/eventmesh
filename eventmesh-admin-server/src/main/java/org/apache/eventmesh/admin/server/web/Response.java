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

package org.apache.eventmesh.admin.server.web;

import org.apache.eventmesh.common.remote.exception.ErrorCode;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Response<T> {

    private int code;

    private boolean success;

    private String desc;

    private T data;

    public static Response<Void> success() {
        Response<Void> response = new Response<>();
        response.success = true;
        response.code = ErrorCode.SUCCESS;
        return response;
    }

    public static <T> Response<T> success(T data) {
        Response<T> response = new Response<>();
        response.success = true;
        response.data = data;
        return response;
    }

    public static Response<Void> fail(int code, String desc) {
        Response<Void> response = new Response<>();
        response.success = false;
        response.code = code;
        response.desc = desc;
        return response;
    }

    public static <T> Response<T> fail(int code, String desc, T data) {
        Response<T> response = new Response<>();
        response.success = false;
        response.code = code;
        response.desc = desc;
        response.data = data;
        return response;
    }
}
