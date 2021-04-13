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

package com.webank.eventmesh.client.http;

import com.webank.eventmesh.client.http.conf.LiteClientConfig;
import com.webank.eventmesh.common.EventMeshException;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

public abstract class AbstractLiteClient {

    public Logger logger = LoggerFactory.getLogger(AbstractLiteClient.class);

    private static CloseableHttpClient wpcli = HttpClients.createDefault();

    public LiteClientConfig liteClientConfig;

    public static final String REGEX_VALIDATE_FOR_RPOXY_DEFAULT;

    static {
        REGEX_VALIDATE_FOR_RPOXY_DEFAULT = "^(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d{4,5};)*(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d{4,5})$";
    }

    public List<String> eventMeshServerList = Lists.newArrayList();

    public AbstractLiteClient(LiteClientConfig liteClientConfig) {
        this.liteClientConfig = liteClientConfig;
    }

    public void start() throws Exception {
        eventMeshServerList = process(liteClientConfig.getLiteEventMeshAddr());
        if(eventMeshServerList == null || eventMeshServerList.size() < 1){
            throw new EventMeshException("liteEventMeshAddr param illegal,please check");
        }
    }

    private List<String> process(String format) {
        List<String> list = Lists.newArrayList();
        if (StringUtils.isNotBlank(format) && format.matches(REGEX_VALIDATE_FOR_RPOXY_DEFAULT)) {

            String[] serversArr = StringUtils.split(format, ";");
            if (ArrayUtils.isNotEmpty(serversArr)) {
                list.addAll(Arrays.asList(serversArr));
            }

            return list;
        }

        logger.error("servers is bad format, servers:{}", format);
        return null;
    }

    public LiteClientConfig getLiteClientConfig() {
        return liteClientConfig;
    }

    public void shutdown() throws Exception {
        logger.info("AbstractLiteClient shutdown");
    }
}
