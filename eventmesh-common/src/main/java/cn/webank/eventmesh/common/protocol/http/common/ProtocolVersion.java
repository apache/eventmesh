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

public enum ProtocolVersion {

    V1("1.0"),
    V2("2.0");

    private String version;

    ProtocolVersion(String version) {
        this.version = version;
    }

    public static ProtocolVersion get(String version) {
        if(V1.version.equals(version)) {
            return V1;
        } else if(V2.version.equals(version)) {
            return V2;
        } else {
            return null;
        }
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public static boolean contains(String version) {
        boolean flag = false;
        for (ProtocolVersion itr : ProtocolVersion.values()) {
            if (itr.version.equals(version)) {
                flag = true;
                break;
            }
        }
        return flag;
    }
}
