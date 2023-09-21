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

package org.apache.eventmesh.common.protocol.grpc.common;

public class ProtocolKey {

    //EventMesh extensions
    public static final String ENV = "env";
    public static final String IDC = "idc";
    public static final String SYS = "sys";
    public static final String PID = "pid";
    public static final String IP = "ip";
    public static final String USERNAME = "username";
    public static final String PASSWD = "passwd";
    public static final String LANGUAGE = "language";
    public static final String PROTOCOL_TYPE = "protocoltype";
    public static final String PROTOCOL_VERSION = "protocolversion";
    public static final String PROTOCOL_DESC = "protocoldesc";
    public static final String SEQ_NUM = "seqnum";
    public static final String UNIQUE_ID = "uniqueid";
    public static final String TTL = "ttl";
    public static final String PRODUCERGROUP = "producergroup";
    public static final String CONSUMERGROUP = "consumergroup";
    public static final String TAG = "tag";
    public static final String CONTENT_TYPE = "contenttype";
    public static final String PROPERTY_MESSAGE_CLUSTER = "cluster";
    public static final String URL = "url";

    public static final String CLIENT_TYPE = "clienttype";

    public static final String GRPC_RESPONSE_CODE = "status_code";
    public static final String GRPC_RESPONSE_MESSAGE = "response_message";
    public static final String GRPC_RESPONSE_TIME = "time";

    /**
     * CloudEvents spec
     *
     * @see <a href="https://github.com/cloudevents/spec/blob/v1.0.2/cloudevents/spec.md#context-attributes">context-attributes</a>
     */

    //Required attributes
    public static final String ID = "id";
    public static final String SOURCE = "source";
    public static final String SPECVERSION = "specversion";
    public static final String TYPE = "type";

    //Optional attributes
    public static final String DATA_CONTENT_TYPE = "datacontenttype";
    public static final String DATA_SCHEMA = "dataschema";
    public static final String SUBJECT = "subject";
    public static final String TIME = "time";
    public static final String EVENT_DATA = "eventdata";
}
