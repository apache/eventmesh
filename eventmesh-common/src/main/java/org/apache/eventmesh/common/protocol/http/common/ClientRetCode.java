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

public enum ClientRetCode {

    /**
     * The "RETRY" means:when the client finds the delivered message and it does not listen, tell EventMesh to send
     * next, try again several times to achieve grayscale, reserve
     */

    REMOTE_OK(0, "REMOTE Process OK"),
    OK(1, "OK. SDK returns."),
    RETRY(2, "RETRY. SDK returns. Retry at most max(default, config) times."),
    FAIL(3, "FAIL. SDK returns."),
    NOLISTEN(5, "NOLISTEN. SDK returns. It can be used for grayscale publishing. Need to try all URLs in this case.");

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
