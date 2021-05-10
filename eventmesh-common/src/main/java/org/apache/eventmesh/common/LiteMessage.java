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

package org.apache.eventmesh.common;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.time.DateFormatUtils;

public class LiteMessage {

    private String bizSeqNo;

    private String uniqueId;

    private String topic;

    private String content;

    private Map<String, String> prop;

    private long createTime = System.currentTimeMillis();

    public LiteMessage() {
    }

    public LiteMessage(String bizSeqno, String uniqueId, String topic,
                       String content) {
        this.bizSeqNo = bizSeqno;
        this.uniqueId = uniqueId;
        this.topic = topic;
        this.content = content;
    }

    public Map<String, String> getProp() {
        return prop;
    }

    public LiteMessage setProp(Map<String, String> prop) {
        this.prop = prop;
        return this;
    }

    public LiteMessage addProp(String key, String val) {
        if (prop == null) {
            prop = new HashMap<String, String>();
        }
        prop.put(key, val);
        return this;
    }

    public String getPropKey(String key) {
        if (prop == null) {
            return null;
        }
        return prop.get(key);
    }

    public LiteMessage removeProp(String key) {
        if (prop == null) {
            return this;
        }
        prop.remove(key);
        return this;
    }

    public String getBizSeqNo() {
        return bizSeqNo;
    }

    public LiteMessage setBizSeqNo(String bizSeqNo) {
        this.bizSeqNo = bizSeqNo;
        return this;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public LiteMessage setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
        return this;
    }

    public String getTopic() {
        return topic;
    }

    public LiteMessage setTopic(String topic) {
        this.topic = topic;
        return this;
    }

    public String getContent() {
        return content;
    }

    public LiteMessage setContent(String content) {
        this.content = content;
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("liteMessage={")
                .append("bizSeqNo=").append(bizSeqNo).append(",")
                .append("uniqueId=").append(uniqueId).append(",")
                .append("topic=").append(topic).append(",")
                .append("content=").append(content).append(",")
                .append("prop=").append(prop).append(",")
                .append("createTime=").append(DateFormatUtils.format(createTime, Constants.DATE_FORMAT))
                .append("}");
        return sb.toString();
    }
}
