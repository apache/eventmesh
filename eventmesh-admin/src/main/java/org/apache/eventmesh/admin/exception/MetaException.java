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

import org.apache.eventmesh.admin.enums.Errors;

/**
 * Meta side exception with EventMeshAdmin Application
 */
public class MetaException extends BaseException {

    private static final long serialVersionUID = 6246145526338359773L;

    public MetaException(String message) {
        super(message);
    }

    /**
     * Customized error reporting with exception
     */
    public MetaException(Errors errors, Throwable cause) {
        super(errors, cause);
    }

    public MetaException(Errors errors) {
        super(errors);
    }
}
