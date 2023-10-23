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

package org.apache.eventmesh.admin.service.impl;

import static org.apache.eventmesh.admin.enums.Errors.NACOS_EMPTY_RESP_ERR;
import static org.apache.eventmesh.admin.enums.Errors.NACOS_GET_CONFIGS_ERR;
import static org.apache.eventmesh.admin.enums.Errors.NACOS_LOGIN_EMPTY_RESP_ERR;
import static org.apache.eventmesh.admin.enums.Errors.NACOS_LOGIN_ERR;
import static org.apache.eventmesh.admin.enums.Errors.NACOS_SDK_CONFIG_ERR;

import org.apache.eventmesh.admin.common.ConfigConst;
import org.apache.eventmesh.admin.common.NacosConst;
import org.apache.eventmesh.admin.config.AdminProperties;
import org.apache.eventmesh.admin.dto.Result;
import org.apache.eventmesh.admin.exception.EventMeshAdminException;
import org.apache.eventmesh.admin.exception.MetaException;
import org.apache.eventmesh.admin.model.SubscriptionInfo;
import org.apache.eventmesh.admin.service.SubscriptionService;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class NacosSubscriptionService implements SubscriptionService {

    AdminProperties adminProperties;

    Properties nacosProps = new Properties();

    RestTemplate restTemplate = new RestTemplate();

    private static String HTTP_PREFIX = ConfigConst.HTTP_PREFIX;

    public NacosSubscriptionService(AdminProperties adminProperties) {
        this.adminProperties = adminProperties;

        nacosProps.setProperty(PropertyKeyConst.SERVER_ADDR, adminProperties.getMeta().getNacos().getAddr());
        nacosProps.setProperty(PropertyKeyConst.NAMESPACE, adminProperties.getMeta().getNacos().getNamespace());
        if (adminProperties.getMeta().getNacos().isAuthEnabled()) {
            if (!adminProperties.getMeta().getNacos().getUsername().isEmpty()) {
                nacosProps.setProperty(PropertyKeyConst.USERNAME, adminProperties.getMeta().getNacos().getUsername());
            }
            if (!adminProperties.getMeta().getNacos().getPassword().isEmpty()) {
                nacosProps.setProperty(PropertyKeyConst.PASSWORD, adminProperties.getMeta().getNacos().getPassword());
            }
            if (!adminProperties.getMeta().getNacos().getAccessKey().isEmpty()) {
                nacosProps.setProperty(PropertyKeyConst.ACCESS_KEY, adminProperties.getMeta().getNacos().getAccessKey());
            }
            if (!adminProperties.getMeta().getNacos().getSecretKey().isEmpty()) {
                nacosProps.setProperty(PropertyKeyConst.SECRET_KEY, adminProperties.getMeta().getNacos().getSecretKey());
            }
        }
        if (adminProperties.getMeta().getNacos().getProtocol().equalsIgnoreCase("https")) {
            HTTP_PREFIX = ConfigConst.HTTPS_PREFIX;
        }
    }

    /**
     * Retrieve a specified config with Nacos SDK.
     */
    @Override
    public String retrieveConfig(String dataId, String group) {
        ConfigService configService;
        try {
            configService = NacosFactory.createConfigService(nacosProps);
        } catch (Exception e) {
            log.error(NACOS_SDK_CONFIG_ERR.getDesc(), e);
            throw new EventMeshAdminException(NACOS_SDK_CONFIG_ERR, e);
        }
        try {
            return configService.getConfig(dataId, group, adminProperties.getConfig().getTimeoutMs());
        } catch (Exception e) {
            log.error(NACOS_GET_CONFIGS_ERR.getDesc(), e);
            throw new MetaException(NACOS_GET_CONFIGS_ERR, e);
        }
    }

    /**
     * Retrieve a list of configs with Nacos OpenAPI, because Nacos SDK doesn't support listing and fuzzy matching.
     */
    @Override
    public Result<List<SubscriptionInfo>> retrieveConfigs(Integer page, Integer size, String dataId, String group) {
        UriComponentsBuilder urlBuilder = UriComponentsBuilder
            .fromHttpUrl(HTTP_PREFIX + nacosProps.getProperty(PropertyKeyConst.SERVER_ADDR) + NacosConst.CONFIGS_API)
            .queryParam(NacosConst.CONFIGS_REQ_PAGE, page)
            .queryParam(NacosConst.CONFIGS_REQ_PAGE_SIZE, size)
            .queryParam(NacosConst.CONFIGS_REQ_DATAID, dataId)
            .queryParam(NacosConst.CONFIGS_REQ_GROUP, group)
            .queryParam(NacosConst.CONFIGS_REQ_SEARCH, "blur");

        if (adminProperties.getMeta().getNacos().isAuthEnabled()) {
            urlBuilder.queryParam(NacosConst.CONFIGS_REQ_TOKEN, loginGetAccessToken());
        }

        ResponseEntity<String> response;
        try {
            response = restTemplate.getForEntity(urlBuilder.toUriString(), String.class);
        } catch (Exception e) {
            log.error(NACOS_GET_CONFIGS_ERR.getDesc(), e);
            throw new MetaException(NACOS_GET_CONFIGS_ERR, e);
        }
        if (response.getBody() == null || response.getBody().isEmpty()) {
            log.error(NACOS_EMPTY_RESP_ERR.getDesc());
            throw new MetaException(NACOS_EMPTY_RESP_ERR);
        }

        JSONObject obj = JSON.parseObject(response.getBody());
        return new Result<>(toSubscriptionInfos(obj), obj.getInteger(NacosConst.CONFIGS_RESP_PAGES));
    }

    private List<SubscriptionInfo> toSubscriptionInfos(JSONObject obj) {
        List<SubscriptionInfo> subscriptionInfos = new ArrayList<>();
        for (Object pageItem : obj.getJSONArray(NacosConst.CONFIGS_RESP_CONTENT_LIST)) {
            JSONObject pageItemObj = (JSONObject) pageItem;
            subscriptionInfos.add(toSubscriptionInfo(pageItemObj));
        }
        return subscriptionInfos;
    }

    private SubscriptionInfo toSubscriptionInfo(JSONObject obj) {
        String content = obj.getString(NacosConst.CONFIGS_RESP_CONTENT);
        return SubscriptionInfo.builder()
            .clientName(obj.getString(NacosConst.CONFIGS_RESP_DATAID))
            .group(obj.getString(NacosConst.CONFIGS_RESP_GROUP))
            // The subscription content of Nacos config should be base64 encoded to protect special characters.
            .subscription(Base64.getEncoder().encodeToString(content.getBytes()))
            .build();
    }

    /**
     * Login if auth enabled and return accessToken.
     */
    private String loginGetAccessToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> bodyParams = new LinkedMultiValueMap<>();
        bodyParams.put(NacosConst.LOGIN_REQ_USERNAME, Collections.singletonList(nacosProps.getProperty(PropertyKeyConst.USERNAME)));
        bodyParams.put(NacosConst.LOGIN_REQ_PASSWORD, Collections.singletonList(nacosProps.getProperty(PropertyKeyConst.PASSWORD)));

        String loginUrl = HTTP_PREFIX + nacosProps.getProperty(PropertyKeyConst.SERVER_ADDR) + NacosConst.LOGIN_API;
        HttpEntity<MultiValueMap<String, String>> loginRequest = new HttpEntity<>(bodyParams, headers);
        ResponseEntity<String> loginResponse;
        try {
            loginResponse = restTemplate.postForEntity(loginUrl, loginRequest, String.class);
        } catch (Exception e) {
            log.error(NACOS_LOGIN_ERR.getDesc(), e);
            throw new MetaException(NACOS_LOGIN_ERR, e);
        }
        if (loginResponse.getBody() == null || loginResponse.getBody().isEmpty()) {
            log.error(NACOS_LOGIN_EMPTY_RESP_ERR + " Status code: {}", loginResponse.getStatusCode());
            throw new MetaException(NACOS_LOGIN_EMPTY_RESP_ERR + " Status code: " + loginResponse.getStatusCode());
        }
        return JSON.parseObject(loginResponse.getBody()).getString(NacosConst.LOGIN_RESP_TOKEN);
    }
}
