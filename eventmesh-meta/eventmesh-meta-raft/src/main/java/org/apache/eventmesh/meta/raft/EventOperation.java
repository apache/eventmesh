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

package org.apache.eventmesh.meta.raft;

import org.apache.eventmesh.meta.raft.consts.MetaRaftConstants;
import org.apache.eventmesh.meta.raft.rpc.RequestResponse;

import java.io.Serializable;
import java.util.Map;


public class EventOperation implements Serializable {

    private static final long serialVersionUID = -6597003954824547294L;

    public static final byte PUT = 0x01;

    public static final byte GET = 0x02;

    public static final byte DELETE = 0x03;

    private byte op;
    private Map<String, String> data;

    public static EventOperation createOpreation(RequestResponse response) {
        if (response.getValue() == MetaRaftConstants.PUT) {
            return new EventOperation(PUT, response.getInfoMap());
        } else if (response.getValue() == MetaRaftConstants.GET) {
            return new EventOperation(GET, response.getInfoMap());
        } else if (response.getValue() == MetaRaftConstants.DELETE) {
            return new EventOperation(DELETE, response.getInfoMap());

        }
        return null;
    }

    public EventOperation(byte op, Map<String, String> data) {
        this.op = op;
        this.data = data;
    }

    public byte getOp() {
        return op;
    }

    public Map<String, String> getData() {
        return data;
    }

    public boolean isReadOp() {
        return GET == this.op;
    }
}
