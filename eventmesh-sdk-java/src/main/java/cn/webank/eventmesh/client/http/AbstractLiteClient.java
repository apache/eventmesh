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

package cn.webank.eventmesh.client.http;

import cn.webank.eventmesh.client.http.conf.LiteClientConfig;
import cn.webank.eventmesh.client.http.http.HttpUtil;
import cn.webank.eventmesh.client.http.http.RequestParam;
import cn.webank.eventmesh.common.Constants;
import cn.webank.eventmesh.common.ProxyException;
import cn.webank.eventmesh.common.ThreadPoolFactory;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.netty.handler.codec.http.HttpMethod;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public abstract class AbstractLiteClient {

    public Logger logger = LoggerFactory.getLogger(AbstractLiteClient.class);

    private static CloseableHttpClient wpcli = HttpClients.createDefault();

    public LiteClientConfig liteClientConfig;

    private String PROXY_SERVER_KEY = "proxyIpList";

    public static final String REGEX_VALIDATE_FOR_RPOXY_4_REGION =
            "^(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d{4,5}\\|[0-9a-zA-Z]{1,15};)*(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d{4,5}\\|[0-9a-zA-Z]{1,15})$";

    public static final String REGEX_VALIDATE_FOR_RPOXY_DEFAULT =
            "^(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d{4,5};)*(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d{4,5})$";

    public Map<String, List<String>> regionsMap = Maps.newConcurrentMap();

    public List<HttpHost> forwardAgentList = Lists.newArrayList();

    private static ScheduledExecutorService scheduledExecutor =
            ThreadPoolFactory.createSingleScheduledExecutor("proxy-fetcher-");

    public AbstractLiteClient(LiteClientConfig liteClientConfig) {
        this.liteClientConfig = liteClientConfig;
    }

    public void start() throws ProxyException {
        if (!liteClientConfig.isRegistryEnabled()) {
            String servers = liteClientConfig.getLiteProxyAddr();
            regionsMap = process(servers);
            return;
        }

        regionsMap = process(loadProxyServers());
        scheduledExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    regionsMap = process(loadProxyServers());
                } catch (ProxyException lse) {
                    logger.error("load proxy server err", lse);
                }
            }
        }, 0, liteClientConfig.getRegistryFetchIntervel(), TimeUnit.MILLISECONDS);

        if (StringUtils.isNotBlank(liteClientConfig.getForwardAgents())) {
            for (String forwardAgent : liteClientConfig.getForwardAgents().split(";")) {
                String host = StringUtils.split(forwardAgent, ":")[0];
                String port = StringUtils.split(forwardAgent, ":")[1];
                forwardAgentList.add(new HttpHost(host, Integer.valueOf(port), "http"));
            }
        }
    }

    public String loadProxyServers() throws ProxyException {
        RequestParam requestParam = new RequestParam(HttpMethod.GET);
        String servers = "";
        try {
            servers = HttpUtil.get(wpcli,
                    String.format("%s/%s", liteClientConfig.getRegistryAddr(), PROXY_SERVER_KEY)
                    , requestParam);
        } catch (Exception ex) {
            throw new ProxyException("load proxy server err", ex);
        }
        return servers;
    }

    private Map process(String format) {
        if (format.matches(REGEX_VALIDATE_FOR_RPOXY_4_REGION)) {
            Map<String, List<String>> tmp = Maps.newConcurrentMap();
            if (StringUtils.isNotBlank(format)) {
                String[] serversArr = StringUtils.split(format, ";");
                for (String sin : serversArr) {
                    String ser = StringUtils.trim(StringUtils.split(sin, "|")[0]);
                    String region = StringUtils.trim(StringUtils.split(sin, "|")[1]);
                    if (tmp.containsKey(region)) {
                        tmp.get(region).add(ser);
                    } else {
                        List<String> list = new ArrayList<>();
                        list.add(ser);
                        tmp.put(region, list);
                    }
                }
            }
            return tmp;
        }

        if (format.matches(REGEX_VALIDATE_FOR_RPOXY_DEFAULT)) {
            List<String> list = Lists.newArrayList();
            if (StringUtils.isNotBlank(format)) {
                String[] serversArr = StringUtils.split(format, ";");
                if (ArrayUtils.isNotEmpty(serversArr)) {
                    for (String server : serversArr) {
                        list.add(server);
                    }
                }
            }

            Map tmp = Maps.newConcurrentMap();
            tmp.put(Constants.CONSTANTS_DEFAULT_REGION_KEY, list);
            return tmp;
        }

        logger.error("servers is bad format, servers:{}", format);
        return null;
    }

    public LiteClientConfig getLiteClientConfig() {
        return liteClientConfig;
    }

    public List<String> getAvailableServers(String region) {
        if (regionsMap.containsKey(region)) {
            return regionsMap.get(region);
        }

        return regionsMap.get(Constants.CONSTANTS_DEFAULT_REGION_KEY);
    }

    public HttpHost getAvailablesForwardAgent() {
        return forwardAgentList.get(RandomUtils.nextInt(0, forwardAgentList.size()));
    }

    public void shutdown() throws Exception {
        scheduledExecutor.shutdown();
    }
}
