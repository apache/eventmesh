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
package org.apache.eventmesh.schema.registry.server.enumerate;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum CompatibilityEnum {
    BACKWARD("BACKWARD", "Consumers using the new schema can read data produced with the last schema."),
    BACKWARD_TRANSITIVE("BACKWARD_TRANSITIVE", "Consumers using the new schema can read data sent by the producer using all previously registered schemas."),
    FORWARD("FORWARD", "Data produced with a new schema can be read by consumers using the last schema."),
    FORWARD_TRANSITIVE("FORWARD_TRANSITIVE", "Data produced with a new schema can be read by consumers using all registered schemas."),
    FULL("FULL", "The new schema is backward and forward compatible with the newly registered schema."),
    FULL_TRANSITIVE("FULL_TRANSITIVE", "The newly registered schema is backward and forward compatible with all previously registered schemas."),
    NONE("NONE", "Schema compatibility checks are disabled.");

    private String name;
    private String description;
}
