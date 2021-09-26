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
package org.apache.eventmesh.schema.registry.server.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SubjectWithSchema {

    public SubjectWithSchema(Subject subject, Schema schema){
        this.subject = subject.getSubject();
        this.tenant = subject.getTenant();
        this.namespace = subject.getNamespace();
        this.app = subject.getApp();
        this.description = subject.getDescription();
        this.status = subject.getStatus();
        this.compatibility = subject.getCompatibility();
        this.coordinate = subject.getCoordinate();
        this.schema = schema;
    }

    private String subject;

    private String tenant;

    private String namespace;

    private String app;

    private String description;

    private String status;

    private String compatibility;

    private String coordinate;

    private Schema schema;
}
