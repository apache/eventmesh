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

package org.apache.eventmesh.protocol.tcp.model;

public class EventMeshTcpSendResult {

    private String seq;

    private EventMeshTcpSendStatus sendStatus;

    private String detail;

    public EventMeshTcpSendResult(String seq, EventMeshTcpSendStatus sendStatus, String detail) {
        this.seq = seq;
        this.sendStatus = sendStatus;
        this.detail = detail;
    }

    public String getSeq() {
        return seq;
    }

    public EventMeshTcpSendStatus getSendStatus() {
        return sendStatus;
    }

    public String getDetail() {
        return detail;
    }

    @Override
    public String toString() {
        return "EventMeshTcpSendResult{seq=" + seq
                + ",sendStatus=" + sendStatus
                + ",detail=" + detail + "}";
    }
}
