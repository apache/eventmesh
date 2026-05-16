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

package cn.webank.eventmesh.client.http.consumer.context;

import cn.webank.eventmesh.common.Constants;
import org.apache.commons.lang3.time.DateFormatUtils;

public class LiteConsumeContext {

    private String proxyIp;

    private String proxyEnv;

    private String proxyRegion;

    private String proxyIdc;

    private String proxyCluster;

    private String proxyDcn;

    //本地RETRY次数
    private int retryTimes = 0;

    private long createTime = System.currentTimeMillis();

    public LiteConsumeContext(String proxyIp, String proxyEnv,
                              String proxyIdc, String proxyRegion,
                              String proxyCluster, String proxyDcn) {
        this.proxyIp = proxyIp;
        this.proxyEnv = proxyEnv;
        this.proxyIdc = proxyIdc;
        this.proxyRegion = proxyRegion;
        this.proxyCluster = proxyCluster;
        this.proxyDcn = proxyDcn;

    }

    public String getProxyIp() {
        return proxyIp;
    }

    public void setProxyIp(String proxyIp) {
        this.proxyIp = proxyIp;
    }

    public String getProxyEnv() {
        return proxyEnv;
    }

    public void setProxyEnv(String proxyEnv) {
        this.proxyEnv = proxyEnv;
    }

    public String getProxyIdc() {
        return proxyIdc;
    }

    public void setProxyIdc(String proxyIdc) {
        this.proxyIdc = proxyIdc;
    }

    public String getProxyCluster() {
        return proxyCluster;
    }

    public void setProxyCluster(String proxyCluster) {
        this.proxyCluster = proxyCluster;
    }

    public String getProxyDcn() {
        return proxyDcn;
    }

    public void setProxyDcn(String proxyDcn) {
        this.proxyDcn = proxyDcn;
    }

    public String getProxyRegion() {
        return proxyRegion;
    }

    public void setProxyRegion(String proxyRegion) {
        this.proxyRegion = proxyRegion;
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
                .append("proxyIp=").append(proxyIp).append(",")
                .append("proxyEnv=").append(proxyEnv).append(",")
                .append("proxyRegion=").append(proxyRegion).append(",")
                .append("proxyIdc=").append(proxyIdc).append(",")
                .append("proxyCluster=").append(proxyCluster).append(",")
                .append("proxyDcn=").append(proxyDcn).append(",")
                .append("retryTimes=").append(retryTimes).append(",")
                .append("createTime=").append(DateFormatUtils.format(createTime, Constants.DATE_FORMAT))
                .append("}");
        return sb.toString();
    }

}
