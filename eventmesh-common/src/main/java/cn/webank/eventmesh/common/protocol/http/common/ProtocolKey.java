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

package cn.webank.eventmesh.common.protocol.http.common;

public class ProtocolKey {

    public static final String REQUEST_CODE = "Code";
    public static final String LANGUAGE = "Language";
    public static final String VERSION = "Version";

    public static class ClientInstanceKey {
        ////////////////////////////////////协议层请求方描述///////////
        public static final String ENV = "Env";
        public static final String REGION = "Region";
        public static final String IDC = "Idc";
        public static final String DCN = "Dcn";
        public static final String SYS = "Sys";
        public static final String PID = "Pid";
        public static final String IP = "Ip";
        public static final String USERNAME = "Username";
        public static final String PASSWD = "Passwd";
    }


    public static class ProxyInstanceKey {
        ///////////////////////////////////////////////协议层PROXY描述
        public static final String PROXYCLUSTER = "ProxyCluster";
        public static final String PROXYIP = "ProxyIp";
        public static final String PROXYENV = "ProxyEnv";
        public static final String PROXYREGION = "ProxyRegion";
        public static final String PROXYIDC = "ProxyIdc";
        public static final String PROXYDCN = "ProxyDcn";
    }


    //CLIENT <-> PROXY 的 返回
    public static final String RETCODE = "retCode";
    public static final String RETMSG = "retMsg";
    public static final String RESTIME = "resTime";
}
