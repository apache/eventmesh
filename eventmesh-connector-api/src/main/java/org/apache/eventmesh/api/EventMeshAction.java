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
package org.apache.eventmesh.api;

<<<<<<<< HEAD:eventmesh-connector-plugin/eventmesh-connector-api/src/main/java/org/apache/eventmesh/api/EventMeshAction.java
package org.apache.eventmesh.api;

public enum EventMeshAction {
    CommitMessage,

========
public enum EventMeshAction {
    CommitMessage,

>>>>>>>> e4cff57da85093ca7a917f7edd86fa434000d5dc:eventmesh-connector-api/src/main/java/org/apache/eventmesh/api/EventMeshAction.java
    ReconsumeLater,

    ManualAck
}
