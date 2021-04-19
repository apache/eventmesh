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

package org.apache.eventmesh.common.protocol.http.body.message;

import org.apache.eventmesh.common.protocol.http.body.Body;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class PushMessageRequestBody extends Body {

    public static final String RANDOMNO = "randomNo";
    public static final String TOPIC = "topic";
    public static final String BIZSEQNO = "bizSeqNo";
    public static final String UNIQUEID = "uniqueId";
    public static final String CONTENT = "content";
    public static final String EXTFIELDS = "extFields";

    private String randomNo;

    private String topic;

    private String content;

    private String bizSeqNo;

    private String uniqueId;

    private HashMap<String, String> extFields;

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getRandomNo() {
        return randomNo;
    }

    public void setRandomNo(String randomNo) {
        this.randomNo = randomNo;
    }

    public String getBizSeqNo() {
        return bizSeqNo;
    }

    public void setBizSeqNo(String bizSeqNo) {
        this.bizSeqNo = bizSeqNo;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public HashMap<String, String> getExtFields() {
        return extFields;
    }

    public void setExtFields(HashMap<String, String> extFields) {
        this.extFields = extFields;
    }

    @SuppressWarnings("unchecked")
    public static PushMessageRequestBody buildBody(final Map<String, Object> bodyParam) {
        PushMessageRequestBody pushMessageRequestBody = new PushMessageRequestBody();
        pushMessageRequestBody.setContent(MapUtils.getString(bodyParam, CONTENT));
        pushMessageRequestBody.setBizSeqNo(MapUtils.getString(bodyParam, BIZSEQNO));
        pushMessageRequestBody.setTopic(MapUtils.getString(bodyParam, TOPIC));
        pushMessageRequestBody.setUniqueId(MapUtils.getString(bodyParam, UNIQUEID));
        pushMessageRequestBody.setRandomNo(MapUtils.getString(bodyParam, RANDOMNO));
        String extFields = MapUtils.getString(bodyParam, EXTFIELDS);

        if (StringUtils.isNotBlank(extFields)) {
            pushMessageRequestBody.setExtFields((HashMap<String, String>) JSONObject.parseObject(extFields, HashMap.class));
        }
        return pushMessageRequestBody;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put(RANDOMNO, randomNo);
        map.put(TOPIC, topic);
        map.put(CONTENT, content);
        map.put(BIZSEQNO, bizSeqNo);
        map.put(UNIQUEID, uniqueId);
        map.put(EXTFIELDS, JSON.toJSONString(extFields));

        return map;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("pushMessageRequestBody={")
                .append("randomNo=").append(randomNo).append(",")
                .append("topic=").append(topic).append(",")
                .append("bizSeqNo=").append(bizSeqNo).append(",")
                .append("uniqueId=").append(uniqueId).append(",")
                .append("content=").append(content).append(",")
                .append("extFields=").append(extFields).append("}");
        return sb.toString();
    }
}
