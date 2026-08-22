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

package cn.webank.eventmesh.common.protocol.http.body.message;

import cn.webank.eventmesh.common.protocol.http.body.Body;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SendMessageBatchRequestBody extends Body {

    public static final String BATCHID = "batchId";
    public static final String CONTENTS = "contents";
    public static final String SIZE = "size";

    private String batchId;

    private List<BatchMessageEntity> contents;

    private String size;

    public SendMessageBatchRequestBody() {
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public List<BatchMessageEntity> getContents() {
        return contents;
    }

    public void setContents(List<BatchMessageEntity> contents) {
        this.contents = contents;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("sendMessageBatchRequestBody={")
                .append("batchId=").append(batchId).append(",")
                .append("size=").append(size).append(",")
                .append("contents=").append(JSON.toJSONString(contents)).append("}");
        return sb.toString();
    }

    public static class BatchMessageEntity {
        public String bizSeqNo;
        public String topic;
        public String msg;
        public String tag;
        public String ttl;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("batchMessageEntity={")
                    .append("bizSeqNo=").append(bizSeqNo).append(",")
                    .append("topic=").append(topic).append(",")
                    .append("msg=").append(msg).append(",")
                    .append("ttl=").append(ttl).append(",")
                    .append("tag=").append(tag).append("}");
            return sb.toString();
        }
    }

    public static SendMessageBatchRequestBody buildBody(final Map<String, Object> bodyParam) {
        String batchId = MapUtils.getString(bodyParam,
                BATCHID);
        String size = StringUtils.isBlank(MapUtils.getString(bodyParam,
                SIZE)) ?  "1" : MapUtils.getString(bodyParam,
                SIZE);
        String contents = MapUtils.getString(bodyParam,
                CONTENTS, null);
        SendMessageBatchRequestBody body = new SendMessageBatchRequestBody();
        body.setBatchId(batchId);
        if (StringUtils.isNotBlank(contents)) {
            body.setContents(JSONArray.parseArray(contents, BatchMessageEntity.class));
        }
        body.setSize(size);
        return body;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put(BATCHID, batchId);
        map.put(SIZE, size);
        map.put(CONTENTS, contents);
        return map;
    }

}
