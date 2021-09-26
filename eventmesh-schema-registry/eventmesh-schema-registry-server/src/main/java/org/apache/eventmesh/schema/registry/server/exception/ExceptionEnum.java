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
package org.apache.eventmesh.schema.registry.server.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ExceptionEnum {

    UnAuthorizedException(HttpStatus.UNAUTHORIZED, "40101", "Unauthorized Exception"),
    SchemaNonExist(HttpStatus.NOT_FOUND,"40401", "Schema Non-exist Exception"),
    SubjectNonExist(HttpStatus.NOT_FOUND, "40402", "Subject Non-exist Exception"),
    VersionNonExist(HttpStatus.NOT_FOUND, "40403", "Version Non-exist Exception"),
    CompatibilityException(HttpStatus.CONFLICT, "40901", "Compatibility Exception"),
    SchemaFormatException(HttpStatus.UNPROCESSABLE_ENTITY, "42201", "Schema Format Exception"),
    SubjectFormatException(HttpStatus.UNPROCESSABLE_ENTITY, "42202", "Subject Format Exception"),
    VersionFormatException(HttpStatus.UNPROCESSABLE_ENTITY, "42203", "Version Format Exception"),
    CompatibilityFormatException(HttpStatus.UNPROCESSABLE_ENTITY, "42204", "Compatibility Format Exception"),
    StorageServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "50001", "Storage Service Exception"),
    TimeoutException(HttpStatus.INTERNAL_SERVER_ERROR, "50002", "Timeout Exception");

    private HttpStatus status;
    private String exceptionCode;
    private String description;
}
