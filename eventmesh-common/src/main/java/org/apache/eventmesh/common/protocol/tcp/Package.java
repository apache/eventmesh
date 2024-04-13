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

package org.apache.eventmesh.common.protocol.tcp;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;

public class Package implements ProtocolTransportObject {

    private static final long serialVersionUID = 3353018029137072737L;
    private transient Header header;
    private Object body;

    public Header getHeader() {
        return header;
    }

    public Object getBody() {
        return body;
    }

    public void setHeader(Header header) {
        this.header = header;
    }

    public void setBody(Object body) {
        this.body = body;
    }

    public Package() {

    }

    public Package(Header header) {
        this.header = header;
    }

    public Package(Header header, Object body) {
        this.header = header;
        this.body = body;
    }

}
