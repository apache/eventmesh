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

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.protocol.http.body.Body;
import org.apache.eventmesh.common.utils.JsonUtils;

public class SendMessageRequestBody extends Body {

    public static final String TOPIC = "topic";
    public static final String BIZSEQNO = "bizSeqNo";
    public static final String UNIQUEID = "uniqueId";
    public static final String CONTENT = "content";
    public static final String TTL = "ttl";
    public static final String TAG = "tag";
    public static final String EXTFIELDS = "extFields";
    public static final String PRODUCERGROUP = "producerGroup";

    private String topic;

    private String bizSeqNo;

    private String uniqueId;

    private String ttl;

    private String content;

    private String tag;

    private HashMap<String, String> extFields;

    private String producerGroup;

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
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

    public String getTtl() {
        return ttl;
    }

    public void setTtl(String ttl) {
        this.ttl = ttl;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getProducerGroup() {
        return producerGroup;
    }

    public void setProducerGroup(String producerGroup) {
        this.producerGroup = producerGroup;
    }

    public static SendMessageRequestBody buildBody(Map<String, Object> bodyParam) {
        SendMessageRequestBody body = new SendMessageRequestBody();
        body.setTopic(MapUtils.getString(bodyParam, TOPIC));
        body.setBizSeqNo(MapUtils.getString(bodyParam, BIZSEQNO));
        body.setUniqueId(MapUtils.getString(bodyParam, UNIQUEID));
        body.setTtl(MapUtils.getString(bodyParam, TTL));
        body.setTag(MapUtils.getString(bodyParam, TAG, ""));
        body.setContent(MapUtils.getString(bodyParam, CONTENT));
        String extFields = MapUtils.getString(bodyParam, EXTFIELDS);
        if (StringUtils.isNotBlank(extFields)) {
            body.setExtFields(JsonUtils.deserialize(extFields, new TypeReference<HashMap<String, String>>() {
            }));
        }
        body.setProducerGroup(MapUtils.getString(bodyParam, PRODUCERGROUP));
        return body;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put(TOPIC, topic);
        map.put(BIZSEQNO, bizSeqNo);
        map.put(UNIQUEID, uniqueId);
        map.put(TTL, ttl);
        map.put(TAG, tag);
        map.put(CONTENT, content);
        map.put(EXTFIELDS, extFields);
        map.put(PRODUCERGROUP, producerGroup);
        return map;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("sendMessageRequestBody={")
                .append("topic=").append(topic).append(",")
                .append("bizSeqNo=").append(bizSeqNo).append(",")
                .append("uniqueId=").append(uniqueId).append(",")
                .append("content=").append(content).append(",")
                .append("ttl=").append(ttl).append(",")
                .append("tag=").append(tag).append(",")
                .append("producerGroup=").append(producerGroup).append(",")
                .append("extFields=").append(extFields).append("}");
        return sb.toString();
    }

}
