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

package org.apache.eventmesh.connector.jdbc.event;

public enum SchemaChangeEventType {

    DATABASE_CREATE("D", "C"),
    DATABASE_DROP("D", "D"),
    DATABASE_ALERT("D", "A"),
    TABLE_CREATE("T", "C"),
    TABLE_DROP("T", "D"),
    TABLE_ALERT("T", "A"),
    ;
    private final String type;
    private final String operationType;

    SchemaChangeEventType(String type, String operationType) {
        this.type = type;
        this.operationType = operationType;
    }

    public String ofType() {
        return this.type;
    }

    public String ofOperationType() {
        return this.operationType;
    }
}
