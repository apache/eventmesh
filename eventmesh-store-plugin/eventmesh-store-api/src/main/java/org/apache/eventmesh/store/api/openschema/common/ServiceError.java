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

package org.apache.eventmesh.store.api.openschema.common;

public enum ServiceError {
    /**
     * 
     */
    ERR_SCHEMA_EMPTY("42201", 442, "42201 - schema info cannot be empty"),

    /**
     * 
     */
    ERR_PERM_NO_AUTH("40101", 401, "40101 - no permission to access"),

    /**
     * 
     */
    ERR_SCHEMA_INVALID("40401", 404, "40401 - schema information does not exist in our record"),
    
    /**
     * 
     */
    ERR_SCHEMA_VERSION_INVALID("40402", 404, "40402 - schema version does not exist in our record"),
    
    /**
     * 
     */
    ERR_SUBJECT_INVALID("40401", 404, "40401 - schema version does not exist in this subject"),
    
    /**
     * 
     */
    ERR_SCHEMA_FORMAT_INVALID("40401", 422, "40401 - schema format is invalid"),
    
    /**
     * 
     */
    ERR_SCHEMA_VERSION_FORMAT_INVALID("40402", 422, "40402 - schema version format is invalid"),
    
    /**
     * 
     */
    ERR_SERVER_ERROR("I50001", 500, "I50001 -  Internal Server Error"),

    /**
     * resource level error definitions
     */
    ERR_RES_NOT_FOUND("50002", 500, "50002 - Request Timeout");

    private String status;

    private int errorCode;

    private String errorMessage;

    ServiceError(String status, int errorCode, String errorMessage) {
        this.status = status;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
    
    public String getStatus() {
        return this.status;
    }

    public int getErrorCode() {
        return this.errorCode;
    }
    
    public String getMessage() {
        return this.errorMessage;
    }
}
