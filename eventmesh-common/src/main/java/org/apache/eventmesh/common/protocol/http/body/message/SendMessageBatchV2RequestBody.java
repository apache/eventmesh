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

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.collections4.MapUtils;
import org.apache.eventmesh.common.protocol.http.body.Body;

public class SendMessageBatchV2RequestBody extends Body {

    public static final String BIZSEQNO = "bizseqno";
    public static final String TOPIC = "topic";
    public static final String MSG = "msg";
    public static final String TAG = "tag";
    public static final String TTL = "ttl";
    public static final String PRODUCERGROUP = "producergroup";

    private String bizSeqNo;

    private String topic;

    private String msg;

    private String tag;

    private String ttl;

    private String producerGroup;

    public String getBizSeqNo() {
        return bizSeqNo;
    }

    public void setBizSeqNo(String bizSeqNo) {
        this.bizSeqNo = bizSeqNo;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getTtl() {
        return ttl;
    }

    public void setTtl(String ttl) {
        this.ttl = ttl;
    }

    public String getProducerGroup() {
        return producerGroup;
    }

    public void setProducerGroup(String producerGroup) {
        this.producerGroup = producerGroup;
    }

    public static SendMessageBatchV2RequestBody buildBody(final Map<String, Object> bodyParam) {
        String bizSeqno = MapUtils.getString(bodyParam,
                BIZSEQNO);
        String topic = MapUtils.getString(bodyParam,
                TOPIC);
        String tag = MapUtils.getString(bodyParam,
                TAG);
        String msg = MapUtils.getString(bodyParam,
                MSG);
        String ttl = MapUtils.getString(bodyParam,
                TTL);
        SendMessageBatchV2RequestBody body = new SendMessageBatchV2RequestBody();
        body.setBizSeqNo(bizSeqno);
        body.setMsg(msg);
        body.setTag(tag);
        body.setTtl(ttl);
        body.setTopic(topic);
        body.setProducerGroup(MapUtils.getString(bodyParam, PRODUCERGROUP));
        return body;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put(BIZSEQNO, bizSeqNo);
        map.put(TOPIC, topic);
        map.put(MSG, msg);
        map.put(TAG, tag);
        map.put(TTL, ttl);
        map.put(PRODUCERGROUP, producerGroup);
        return map;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SendMessageBatchV2RequestBody={")
                .append("bizSeqNo=").append(bizSeqNo).append(",")
                .append("topic=").append(topic).append(",")
                .append("tag=").append(tag).append(",")
                .append("ttl=").append(ttl).append(",")
                .append("producerGroup=").append(producerGroup).append(",")
                .append("msg=").append(msg).append("}");
        return sb.toString();
    }
}
