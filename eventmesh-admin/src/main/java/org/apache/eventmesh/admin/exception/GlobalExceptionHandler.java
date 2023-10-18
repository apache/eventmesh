package org.apache.eventmesh.admin.exception;

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

import org.apache.eventmesh.admin.dto.Result;

import javax.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<Result<Object>> baseHandler(BaseException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.error("RESTful API {} service error occurred: ", requestURI, e);
        return Result.internalError(e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Result<Object>> runtimeHandler(RuntimeException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.error("RESTful API {} runtime error occurred: ", requestURI, e);
        return Result.internalError(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Object>> exceptionHandler(Exception e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.error("RESTful API {} unknown error occurred: ", requestURI, e);
        return Result.internalError(e.getMessage());
    }

}
