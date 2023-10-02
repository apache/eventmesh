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

package org.apache.eventmesh.adminkotlin.service.impl;

import static org.apache.eventmesh.adminkotlin.config.Constants.NACOS_CONFIGS_API;
import static org.apache.eventmesh.adminkotlin.config.Constants.NACOS_LOGIN_API;

import org.apache.eventmesh.adminkotlin.config.AdminProperties;
import org.apache.eventmesh.adminkotlin.config.Constants;
import org.apache.eventmesh.adminkotlin.dto.SubscriptionResponse;
import org.apache.eventmesh.adminkotlin.exception.EventMeshAdminException;
import org.apache.eventmesh.adminkotlin.exception.MetaException;
import org.apache.eventmesh.adminkotlin.model.SubscriptionInfo;
import org.apache.eventmesh.adminkotlin.service.SubscriptionService;

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

    private static String HTTP_PREFIX = Constants.HTTP_PREFIX;

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
            HTTP_PREFIX = Constants.HTTPS_PREFIX;
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
            log.error("Failed to create Nacos ConfigService", e);
            throw new EventMeshAdminException("Failed to create Nacos ConfigService: " + e.getMessage());
        }
        try {
            return configService.getConfig(dataId, group, adminProperties.getConfig().getTimeoutMs());
        } catch (Exception e) {
            log.error("Failed to retrieve Nacos config", e);
            throw new MetaException("Failed to retrieve Nacos config: " + e.getMessage());
        }
    }

    /**
     * Retrieve a list of configs with Nacos OpenAPI, because Nacos SDK doesn't support listing and fuzzy matching.
     */
    @Override
    public SubscriptionResponse retrieveConfigs(Integer page, Integer size, String dataId, String group) {
        UriComponentsBuilder urlBuilder = UriComponentsBuilder
            .fromHttpUrl(HTTP_PREFIX + nacosProps.getProperty(PropertyKeyConst.SERVER_ADDR) + NACOS_CONFIGS_API)
            .queryParam("pageNo", page)
            .queryParam("pageSize", size)
            .queryParam("dataId", dataId)
            .queryParam("group", group)
            .queryParam("search", "blur");

        if (adminProperties.getMeta().getNacos().isAuthEnabled()) {
            urlBuilder.queryParam("accessToken", loginGetAccessToken());
        }

        ResponseEntity<String> response;
        try {
            response = restTemplate.getForEntity(urlBuilder.toUriString(), String.class);
        } catch (Exception e) {
            log.error("Failed to retrieve Nacos config list.", e);
            throw new MetaException("Failed to retrieve Nacos config list: " + e.getMessage());
        }
        if (response.getBody() == null || response.getBody().isEmpty()) {
            log.error("No result returned by Nacos. Please check Nacos.");
            throw new MetaException("No result returned by Nacos. Please check Nacos.");
        }

        return toSubscriptionResponse(JSON.parseObject(response.getBody()));
    }

    private SubscriptionResponse toSubscriptionResponse(JSONObject obj) {
        return SubscriptionResponse.builder()
            .subscriptionInfos(toSubscriptionInfos(obj))
            .pages(obj.getInteger("pagesAvailable"))
            .message("success")
            .build();
    }

    private List<SubscriptionInfo> toSubscriptionInfos(JSONObject obj) {
        List<SubscriptionInfo> subscriptionInfos = new ArrayList<>();
        for (Object pageItem : obj.getJSONArray("pageItems")) {
            JSONObject pageItemObj = (JSONObject) pageItem;
            subscriptionInfos.add(toSubscriptionInfo(pageItemObj));
        }
        return subscriptionInfos;
    }

    private SubscriptionInfo toSubscriptionInfo(JSONObject obj) {
        String content = obj.getString("content");
        return SubscriptionInfo.builder()
            .clientName(obj.getString("dataId"))
            .group(obj.getString("group"))
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
        bodyParams.put("username", Collections.singletonList(nacosProps.getProperty(PropertyKeyConst.USERNAME)));
        bodyParams.put("password", Collections.singletonList(nacosProps.getProperty(PropertyKeyConst.PASSWORD)));

        String loginUrl = HTTP_PREFIX + nacosProps.getProperty(PropertyKeyConst.SERVER_ADDR) + NACOS_LOGIN_API;
        HttpEntity<MultiValueMap<String, String>> loginRequest = new HttpEntity<>(bodyParams, headers);
        ResponseEntity<String> loginResponse;
        try {
            loginResponse = restTemplate.postForEntity(loginUrl, loginRequest, String.class);
        } catch (Exception e) {
            log.error("Nacos login failed.", e);
            throw new MetaException("Nacos login failed: " + e.getMessage());
        }
        if (loginResponse.getBody() == null || loginResponse.getBody().isEmpty()) {
            log.error("Nacos didn't return accessToken. Please check Nacos status. Status code: {}", loginResponse.getStatusCode());
            throw new MetaException("Nacos didn't return accessToken. Please check Nacos status. Status code: " + loginResponse.getStatusCode());
        }
        return JSON.parseObject(loginResponse.getBody()).getString("accessToken");
    }
}
