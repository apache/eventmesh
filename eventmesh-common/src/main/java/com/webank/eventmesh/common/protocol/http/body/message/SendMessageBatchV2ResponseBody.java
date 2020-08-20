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

package com.webank.eventmesh.common.protocol.http.body.message;

import com.webank.eventmesh.common.Constants;
import com.webank.eventmesh.common.protocol.http.body.Body;
import com.webank.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.commons.lang3.time.DateFormatUtils;

import java.util.HashMap;
import java.util.Map;

public class SendMessageBatchV2ResponseBody extends Body {

    //响应码
    private Integer retCode;

    //响应信息
    private String retMsg;

    //回复时间
    private long resTime = System.currentTimeMillis();

    public Integer getRetCode() {
        return retCode;
    }

    public void setRetCode(Integer retCode) {
        this.retCode = retCode;
    }

    public String getRetMsg() {
        return retMsg;
    }

    public void setRetMsg(String retMsg) {
        this.retMsg = retMsg;
    }

    public long getResTime() {
        return resTime;
    }

    public void setResTime(long resTime) {
        this.resTime = resTime;
    }

    public static SendMessageBatchV2ResponseBody buildBody(Integer retCode, String retMsg) {
        SendMessageBatchV2ResponseBody sendMessageBatchV2ResponseBody = new SendMessageBatchV2ResponseBody();
        sendMessageBatchV2ResponseBody.setRetMsg(retMsg);
        sendMessageBatchV2ResponseBody.setRetCode(retCode);
        sendMessageBatchV2ResponseBody.setResTime(System.currentTimeMillis());
        return sendMessageBatchV2ResponseBody;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("sendMessageBatchV2ResponseBody={")
                .append("retCode=").append(retCode).append(",")
                .append("retMsg=").append(retMsg).append(",")
                .append("resTime=").append(DateFormatUtils.format(resTime, Constants.DATE_FORMAT)).append("}");
        return sb.toString();
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put(ProtocolKey.RETCODE, retCode);
        map.put(ProtocolKey.RETMSG, retMsg);
        map.put(ProtocolKey.RESTIME, resTime);
        return map;
    }
}
