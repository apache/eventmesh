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

package com.webank.eventmesh.common.protocol.http.common;

public enum ClientRetCode {

    /**
     * 这个RETRY的意思是 客户端发现投递的消息它没有监听时, 告诉EventMesh 发往下一个, 重试几次以实现灰度 , 预留
     */

    OK(1, "ok, SDK返回"),
    RETRY(2, "retry, SDK返回, 这种情况需要尝试至多 max(default, config)"),
    FAIL(3, "fail, SDK返回"),
    NOLISTEN(5, "没有监听, SDK返回, 可用于灰度发布实现, 这种情况需要尝试完所有的url");

    ClientRetCode(Integer retCode, String errMsg) {
        this.retCode = retCode;
        this.errMsg = errMsg;
    }

    public static boolean contains(Integer clientRetCode) {
        boolean flag = false;
        for (ClientRetCode itr : ClientRetCode.values()) {
            if (itr.retCode.intValue() == clientRetCode.intValue()) {
                flag = true;
                break;
            }
        }
        return flag;
    }

    public static ClientRetCode get(Integer clientRetCode) {
        ClientRetCode ret = null;
        for (ClientRetCode itr : ClientRetCode.values()) {
            if (itr.retCode.intValue() == clientRetCode.intValue()) {
                ret = itr;
                break;
            }
        }
        return ret;
    }

    private Integer retCode;

    private String errMsg;

    public Integer getRetCode() {
        return retCode;
    }

    public void setRetCode(Integer retCode) {
        this.retCode = retCode;
    }

    public String getErrMsg() {
        return errMsg;
    }

    public void setErrMsg(String errMsg) {
        this.errMsg = errMsg;
    }
}
