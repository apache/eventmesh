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

<<<<<<<< HEAD:eventmesh-emesher/src/main/java/cn/webank/emesher/core/protocol/tcp/client/session/SessionState.java
package cn.webank.emesher.core.protocol.tcp.client.session;
========
package com.webank.runtime.core.protocol.tcp.client.session;
>>>>>>>> 43bf39791 (event mesh project architecture adjustment):eventmesh-runtime/src/main/java/com/webank/runtime/core/protocol/tcp/client/session/SessionState.java

public enum SessionState {
    CREATED,
    RUNNING,
    CLOSED
}
