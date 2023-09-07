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

package org.apache.eventmesh.runtime.core.protocol.http.processor.inf;

import static org.apache.eventmesh.runtime.constants.EventMeshConstants.CONTENT_TYPE;

import org.apache.eventmesh.api.meta.dto.EventMeshDataInfo;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.http.HttpEventWrapper;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.utils.AssertUtils;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.common.utils.ThreadUtils;
import org.apache.eventmesh.meta.nacos.constant.NacosConstant;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupMetadata;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupTopicMetadata;
import org.apache.eventmesh.runtime.core.protocol.http.processor.AsyncHttpProcessor;
import org.apache.eventmesh.runtime.meta.MetaStorage;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.collect.Maps;

import lombok.extern.slf4j.Slf4j;

/**
 * EventProcessor
 */
@Slf4j
public abstract class AbstractEventProcessor implements AsyncHttpProcessor {

    protected transient EventMeshHTTPServer eventMeshHTTPServer;

    public AbstractEventProcessor(EventMeshHTTPServer eventMeshHTTPServer) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
    }

    protected String getTargetMesh(String consumerGroup, List<SubscriptionItem> subscriptionList)
        throws Exception {
        // Currently only supports http
        CommonConfiguration httpConfiguration = eventMeshHTTPServer.getEventMeshHttpConfiguration();
        if (!httpConfiguration.isEventMeshServerMetaStorageEnable()) {
            return "";
        }

        String targetMesh = "";
        MetaStorage metaStorage = eventMeshHTTPServer.getMetaStorage();
        List<EventMeshDataInfo> allEventMeshInfo = metaStorage.findAllEventMeshInfo();
        String httpServiceName =
            ConfigurationContextUtil.HTTP + "-" + NacosConstant.GROUP + "@@" + httpConfiguration.getEventMeshName()
                + "-" + ConfigurationContextUtil.HTTP;
        for (EventMeshDataInfo eventMeshDataInfo : allEventMeshInfo) {
            if (!eventMeshDataInfo.getEventMeshName().equals(httpServiceName)) {
                continue;
            }

            if (httpConfiguration.getEventMeshCluster().equals(eventMeshDataInfo.getEventMeshClusterName())) {
                continue;
            }

            Map<String, String> metadata = eventMeshDataInfo.getMetadata();
            String topicMetadataJson = metadata.get(consumerGroup);
            if (StringUtils.isBlank(topicMetadataJson)) {
                continue;
            }

            ConsumerGroupMetadata consumerGroupMetadata =
                JsonUtils.parseObject(topicMetadataJson, ConsumerGroupMetadata.class);
            Map<String, ConsumerGroupTopicMetadata> consumerGroupTopicMetadataMap =
                Optional.ofNullable(consumerGroupMetadata)
                    .map(ConsumerGroupMetadata::getConsumerGroupTopicMetadataMap)
                    .orElseGet(Maps::newConcurrentMap);

            for (SubscriptionItem subscriptionItem : subscriptionList) {
                if (consumerGroupTopicMetadataMap.containsKey(subscriptionItem.getTopic())) {
                    targetMesh = "http://" + eventMeshDataInfo.getEndpoint() + "/eventmesh/subscribe/local";
                    break;
                }
            }
            break;
        }
        return targetMesh;
    }

    /**
     * builder response header map
     *
     * @param requestWrapper requestWrapper
     * @return Map
     */
    protected Map<String, Object> builderResponseHeaderMap(HttpEventWrapper requestWrapper) {
        Map<String, Object> responseHeaderMap = new HashMap<>();
        EventMeshHTTPConfiguration eventMeshHttpConfiguration = eventMeshHTTPServer.getEventMeshHttpConfiguration();
        responseHeaderMap.put(ProtocolKey.REQUEST_URI, requestWrapper.getRequestURI());
        responseHeaderMap.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHCLUSTER,
            eventMeshHttpConfiguration.getEventMeshCluster());
        responseHeaderMap.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHIP,
            IPUtils.getLocalAddress());
        responseHeaderMap.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHENV,
            eventMeshHttpConfiguration.getEventMeshEnv());
        responseHeaderMap.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHIDC,
            eventMeshHttpConfiguration.getEventMeshIDC());
        return responseHeaderMap;
    }

    /**
     * validation sysHeaderMap is null
     *
     * @param sysHeaderMap sysHeaderMap
     * @return Returns true if any is empty
     */
    protected boolean validateSysHeader(Map<String, Object> sysHeaderMap) {
        return StringUtils.isAnyBlank(sysHeaderMap.get(ProtocolKey.ClientInstanceKey.IDC.getKey()).toString(),
            sysHeaderMap.get(ProtocolKey.ClientInstanceKey.PID.getKey()).toString(),
            sysHeaderMap.get(ProtocolKey.ClientInstanceKey.SYS.getKey()).toString())
            || !StringUtils.isNumeric(sysHeaderMap.get(ProtocolKey.ClientInstanceKey.PID.getKey()).toString());
    }

    /**
     * validation requestBodyMap key url topic consumerGroup is any null
     *
     * @param requestBodyMap requestBodyMap
     * @return any null then true
     */
    protected boolean validatedRequestBodyMap(Map<String, Object> requestBodyMap) {
        return requestBodyMap.get(EventMeshConstants.MANAGE_TOPIC) == null;

    }

    /**
     * builder RemoteHeaderMap
     *
     * @param localAddress
     * @return
     */
    protected Map<String, String> builderRemoteHeaderMap(String localAddress) {
        EventMeshHTTPConfiguration eventMeshHttpConfiguration = this.eventMeshHTTPServer.getEventMeshHttpConfiguration();
        String meshGroup = eventMeshHttpConfiguration.getMeshGroup();

        Map<String, String> remoteHeaderMap = new HashMap<>();
        remoteHeaderMap.put(ProtocolKey.ClientInstanceKey.ENV.getKey(), eventMeshHttpConfiguration.getEventMeshEnv());
        remoteHeaderMap.put(ProtocolKey.ClientInstanceKey.IDC.getKey(), eventMeshHttpConfiguration.getEventMeshIDC());
        remoteHeaderMap.put(ProtocolKey.ClientInstanceKey.IP.getKey(), localAddress);
        remoteHeaderMap.put(ProtocolKey.ClientInstanceKey.PID.getKey(), String.valueOf(ThreadUtils.getPID()));
        remoteHeaderMap.put(ProtocolKey.ClientInstanceKey.SYS.getKey(), eventMeshHttpConfiguration.getSysID());
        remoteHeaderMap.put(ProtocolKey.ClientInstanceKey.USERNAME.getKey(), EventMeshConstants.USER_NAME);
        remoteHeaderMap.put(ProtocolKey.ClientInstanceKey.PASSWD.getKey(), EventMeshConstants.PASSWD);
        remoteHeaderMap.put(ProtocolKey.ClientInstanceKey.PRODUCERGROUP.getKey(), meshGroup);
        remoteHeaderMap.put(ProtocolKey.ClientInstanceKey.CONSUMERGROUP.getKey(), meshGroup);
        return remoteHeaderMap;
    }

    /**
     * http post
     *
     * @param client          client
     * @param uri             uri
     * @param requestHeader   requestHeader
     * @param requestBody     requestBody
     * @param responseHandler responseHandler
     * @return string
     * @throws IOException
     */
    public static String post(CloseableHttpClient client, String uri,
        Map<String, String> requestHeader, Map<String, Object> requestBody,
        ResponseHandler<String> responseHandler) throws IOException {
        AssertUtils.notNull(client, "client can't be null");
        AssertUtils.notBlank(uri, "uri can't be null");
        AssertUtils.notNull(requestHeader, "requestParam can't be null");
        AssertUtils.notNull(responseHandler, "responseHandler can't be null");

        HttpPost httpPost = new HttpPost(uri);

        httpPost.addHeader(CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());

        //header
        if (MapUtils.isNotEmpty(requestHeader)) {
            requestHeader.forEach(httpPost::addHeader);
        }

        //body
        if (MapUtils.isNotEmpty(requestBody)) {
            String jsonStr = Optional.ofNullable(JsonUtils.toJSONString(requestBody)).orElse("");
            httpPost.setEntity(new StringEntity(jsonStr, ContentType.APPLICATION_JSON));
        }

        //ttl
        RequestConfig.Builder configBuilder = RequestConfig.custom();
        configBuilder.setSocketTimeout(Integer.parseInt(String.valueOf(Constants.DEFAULT_HTTP_TIME_OUT)))
            .setConnectTimeout(Integer.parseInt(String.valueOf(Constants.DEFAULT_HTTP_TIME_OUT)))
            .setConnectionRequestTimeout(Integer.parseInt(String.valueOf(Constants.DEFAULT_HTTP_TIME_OUT)));

        httpPost.setConfig(configBuilder.build());

        return client.execute(httpPost, responseHandler);
    }

}
