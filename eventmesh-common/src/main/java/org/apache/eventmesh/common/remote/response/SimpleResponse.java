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

package org.apache.eventmesh.common.remote.response;

import org.apache.eventmesh.common.remote.exception.ErrorCode;

public class SimpleResponse extends BaseRemoteResponse {
    /**
     * just mean remote received or process success
     */
    public static SimpleResponse success() {
        return new SimpleResponse();
    }

    public static SimpleResponse fail(int errorCode, String msg) {
        SimpleResponse response = new SimpleResponse();
        response.setErrorCode(errorCode);
        response.setDesc(msg);
        response.setSuccess(false);
        return response;
    }


    /**
     * build an error response.
     *
     * @param exception exception
     * @return response
     */
    public static SimpleResponse fail(Throwable exception) {
        return fail(ErrorCode.INTERNAL_ERR, exception.getMessage());
    }
}
