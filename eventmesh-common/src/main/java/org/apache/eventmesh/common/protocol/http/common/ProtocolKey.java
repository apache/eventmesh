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

package org.apache.eventmesh.common.protocol.http.common;

public class ProtocolKey {

    public static final String REQUEST_CODE = "code";
    public static final String LANGUAGE = "language";
    public static final String VERSION = "version";

    public static final String PROTOCOL_TYPE = "protocoltype";

    public static final String PROTOCOL_VERSION = "protocolversion";

    public static final String PROTOCOL_DESC = "protocoldesc";

    public static class ClientInstanceKey {
        ////////////////////////////////////Protocol layer requester description///////////
        public static final String ENV = "env";
        public static final String IDC = "idc";
        public static final String SYS = "sys";
        public static final String PID = "pid";
        public static final String IP = "ip";
        public static final String USERNAME = "username";
        public static final String PASSWD = "passwd";
    }


    public static class EventMeshInstanceKey {
        ///////////////////////////////////////////////Protocol layer EventMesh description
        public static final String EVENTMESHCLUSTER = "eventmeshcluster";
        public static final String EVENTMESHIP = "eventmeship";
        public static final String EVENTMESHENV = "eventmeshenv";
        public static final String EVENTMESHIDC = "eventmeshidc";
    }


    //return of CLIENT <-> EventMesh
    public static final String RETCODE = "retCode";
    public static final String RETMSG = "retMsg";
    public static final String RESTIME = "resTime";
}
