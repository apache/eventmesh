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

package org.apache.eventmesh.client.http.consumer.context;

import org.apache.eventmesh.common.Constants;

import org.apache.commons.lang3.time.DateFormatUtils;

public class LiteConsumeContext {

    private String eventMeshIp;

    private String eventMeshEnv;

    private String eventMeshIdc;

    private String eventMeshCluster;

    // Local RETRY times
    private int retryTimes = 0;

    private long createTime = System.currentTimeMillis();

    public LiteConsumeContext(String eventMeshIp, String eventMeshEnv,
                              String eventMeshIdc,
                              String eventMeshCluster) {
        this.eventMeshIp = eventMeshIp;
        this.eventMeshEnv = eventMeshEnv;
        this.eventMeshIdc = eventMeshIdc;
        this.eventMeshCluster = eventMeshCluster;

    }

    public String getEventMeshIp() {
        return eventMeshIp;
    }

    public void setEventMeshIp(String eventMeshIp) {
        this.eventMeshIp = eventMeshIp;
    }

    public String getEventMeshEnv() {
        return eventMeshEnv;
    }

    public void setEventMeshEnv(String eventMeshEnv) {
        this.eventMeshEnv = eventMeshEnv;
    }

    public String getEventMeshIdc() {
        return eventMeshIdc;
    }

    public void setEventMeshIdc(String eventMeshIdc) {
        this.eventMeshIdc = eventMeshIdc;
    }

    public String getEventMeshCluster() {
        return eventMeshCluster;
    }

    public void setEventMeshCluster(String eventMeshCluster) {
        this.eventMeshCluster = eventMeshCluster;
    }

    public int getRetryTimes() {
        return retryTimes;
    }

    public void setRetryTimes(int retryTimes) {
        this.retryTimes = retryTimes;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("liteConsumeContext={")
                .append("eventMeshIp=").append(eventMeshIp).append(",")
                .append("eventMeshEnv=").append(eventMeshEnv).append(",")
                .append("eventMeshIdc=").append(eventMeshIdc).append(",")
                .append("eventMeshCluster=").append(eventMeshCluster).append(",")
                .append("retryTimes=").append(retryTimes).append(",")
                .append("createTime=").append(DateFormatUtils.format(createTime, Constants.DATE_FORMAT))
                .append("}");
        return sb.toString();
    }

}
