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

public enum ClientType {

    PUB(1, "发送客户端"),

    SUB(2, "监听客户端");

    private Integer type;

    private String desc;

    ClientType(Integer type, String desc) {
        this.type = type;
        this.desc = desc;
    }

    public static ClientType get(Integer type) {
        if (PUB.type.intValue() == type.intValue()) {
            return PUB;
        } else if (SUB.type.intValue() == type.intValue()) {
            return SUB;
        } else {
            return null;
        }
    }

    public static boolean contains(Integer clientType) {
        boolean flag = false;
        for(ClientType ct:ClientType.values()) {
            if(ct.type == clientType.intValue()) {
                flag = true;
                break;
            }
        }
        return flag;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }
}
