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

import static org.apache.eventmesh.admin.constant.ConfigConst.COLON;

import org.apache.eventmesh.admin.enums.Status;
import org.apache.eventmesh.admin.util.ExceptionUtil;

import lombok.Getter;

/**
 * Exceptions in EventMeshAdmin application
 */

@Getter
public class BaseException extends RuntimeException {

    private static final long serialVersionUID = 3509261993355721168L;

    private Status status;

    public BaseException(String message) {
        super(message);
    }

    /**
     * Customized error reporting using enums and exceptions
     */
    public BaseException(Status status, Throwable cause) {
        super(ExceptionUtil.trimDesc(status.getDesc()) + COLON + cause.getMessage(), cause);
        this.status = status;
    }

    public BaseException(Status status) {
        super(status.getDesc());
        this.status = status;
    }
}
