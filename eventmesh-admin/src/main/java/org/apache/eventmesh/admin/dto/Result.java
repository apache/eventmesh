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

package org.apache.eventmesh.admin.dto;

import static org.apache.eventmesh.admin.enums.Errors.SUCCESS;

import org.apache.eventmesh.admin.enums.Errors;
import org.apache.eventmesh.admin.exception.BaseException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A RESTful response DTO.
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    private T data;

    private Integer pages;

    private Message message;

    public Result(Message message) {
        this.message = message;
    }

    public Result(T data, Integer pages) {
        this.data = data;
        this.pages = pages;
    }

    /**
     * The request is valid and the result is wrapped in {@link Result}.
     */
    public static <T> Result<T> success() {
        return new Result<>(new Message(SUCCESS));
    }

    public static <T> Result<T> success(Result<T> result) {
        result.setMessage(new Message(SUCCESS));
        return result;
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(data, null, new Message(SUCCESS));
    }

    /**
     * The request is valid and the result is returned in {@link ResponseEntity}.
     * Logic issues should use 422 Unprocessable Entity instead of 200 OK.
     */
    public static <T> ResponseEntity<Result<T>> ok() {
        return ResponseEntity.ok(new Result<>(new Message(SUCCESS)));
    }

    public static <T> ResponseEntity<Result<T>> ok(Result<T> result) {
        result.setMessage(new Message(SUCCESS));
        return ResponseEntity.ok(result);
    }

    /**
     * The request is invalid.
     */
    public static <T> ResponseEntity<Result<T>> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new Result<>(new Message(message)));
    }

    /**
     * The request is valid but cannot be processed due to business logic issues.
     */
    public static <T> ResponseEntity<Result<T>> unprocessable(String message) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new Result<>(new Message(message)));
    }

    /**
     * Uncaught exception happened in EventMeshAdmin application.
     */
    public static <T> ResponseEntity<Result<T>> internalError(String message) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new Result<>(new Message(message)));
    }

    /**
     * Upstream service unavailable such as Meta.
     */
    public static <T> ResponseEntity<Result<T>> badGateway(String message) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new Result<>(new Message(message)));
    }

    @Data
    public static class Message {

        private String name;

        private String type;

        private String desc;

        public Message(BaseException e) {
            this.name = e.getErrors().name();
            this.type = e.getErrors().getType().name();
            this.desc = e.getMessage();
        }

        /**
         * Only recommended for returning successful results,
         * the stack trace cannot be displayed when returning unsuccessful results.
         */
        public Message(Errors errors) {
            this.name = errors.name();
            this.type = errors.getType().name();
            this.desc = errors.getDesc(); // no stack trace
        }

        public Message(String desc) {
            this.desc = desc;
        }
    }
}
