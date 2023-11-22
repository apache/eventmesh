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

package org.apache.eventmesh.admin.exception;

import org.apache.eventmesh.admin.dto.Result;
import org.apache.eventmesh.admin.dto.Result.StatusMessage;

import javax.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

/**
 * This class, in conjunction with {@linkplain org.apache.eventmesh.admin.enums.Status Status} and {@link BaseException},
 * collectively implements customized error reporting.
 */

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<Result<Object>> baseHandler(BaseException e, HttpServletRequest request) {
        String uri = request.getRequestURI();
        log.error("RESTful API {} service error occurred, name: {}, category: {}",
            uri, e.getStatus().name(), e.getStatus().getCategory().name(), e);
        return ResponseEntity.status(e.getStatus().getCode()).body(new Result<>(new StatusMessage(e)));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Result<Object>> runtimeHandler(RuntimeException e, HttpServletRequest request) {
        String uri = request.getRequestURI();
        log.error("RESTful API {} runtime error occurred.", uri, e);
        return Result.internalError(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Object>> exceptionHandler(Exception e, HttpServletRequest request) {
        String uri = request.getRequestURI();
        log.error("RESTful API {} unknown error occurred.", uri, e);
        return Result.internalError(e.getMessage());
    }

}
