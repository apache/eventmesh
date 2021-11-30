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
import org.apache.eventmesh.common.utils.JsonUtils;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;

public class ReplyMessageRequestBody extends Body {

    public static final String ORIGTOPIC     = "origtopic";
    public static final String BIZSEQNO      = "bizseqno";
    public static final String UNIQUEID      = "uniqueId";
    public static final String CONTENT       = "content";
    public static final String EXTFIELDS     = "extFields";
    public static final String PRODUCERGROUP = "producergroup";

    private String bizSeqNo;

    private String uniqueId;

    private String content;

    private String origTopic;

    private HashMap<String, String> extFields;

    private String producerGroup;

    public String getOrigTopic() {
        return origTopic;
    }

    public void setOrigTopic(String origTopic) {
        this.origTopic = origTopic;
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

    public String getProducerGroup() {
        return producerGroup;
    }

    public void setProducerGroup(String producerGroup) {
        this.producerGroup = producerGroup;
    }

    public static ReplyMessageRequestBody buildBody(Map<String, Object> bodyParam) {
        ReplyMessageRequestBody body = new ReplyMessageRequestBody();
        body.setBizSeqNo(MapUtils.getString(bodyParam, BIZSEQNO));
        body.setUniqueId(MapUtils.getString(bodyParam, UNIQUEID));
        body.setContent(MapUtils.getString(bodyParam, CONTENT));
        body.setOrigTopic(MapUtils.getString(bodyParam, ORIGTOPIC));
        String extFields = MapUtils.getString(bodyParam, EXTFIELDS);
        if (StringUtils.isNotBlank(extFields)) {
            body.setExtFields(
                JsonUtils.deserialize(extFields, new TypeReference<HashMap<String, String>>() {
                }));
        }
        body.setProducerGroup(MapUtils.getString(bodyParam, PRODUCERGROUP));
        return body;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("replyMessageRequestBody={")
            .append("bizSeqNo=").append(bizSeqNo).append(",")
            .append("uniqueId=").append(uniqueId).append(",")
            .append("origTopic=").append(origTopic).append(",")
            .append("content=").append(content).append(",")
            .append("producerGroup=").append(producerGroup).append(",")
            .append("extFields=").append(extFields).append("}");
        return sb.toString();
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put(BIZSEQNO, bizSeqNo);
        map.put(ORIGTOPIC, origTopic);
        map.put(UNIQUEID, uniqueId);
        map.put(CONTENT, content);
        map.put(EXTFIELDS, JsonUtils.serialize(extFields));
        map.put(PRODUCERGROUP, producerGroup);
        return map;
    }
}
