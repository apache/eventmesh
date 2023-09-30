package com.apache.eventmesh.adminkotlin.service.impl;

import static com.apache.eventmesh.adminkotlin.config.Constants.HTTP_PREFIX;
import static com.apache.eventmesh.adminkotlin.config.Constants.NACOS_CONFIGS_API;
import static com.apache.eventmesh.adminkotlin.config.Constants.NACOS_LOGIN_API;

import java.util.Collections;
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
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.apache.eventmesh.adminkotlin.config.AdminProperties;
import com.apache.eventmesh.adminkotlin.dto.CommonResponse;
import com.apache.eventmesh.adminkotlin.service.SubscriptionService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class NacosSubscriptionService implements SubscriptionService {

    AdminProperties adminProperties;

    Properties nacosProps = new Properties();

    RestTemplate restTemplate = new RestTemplate();

    public NacosSubscriptionService(AdminProperties adminProperties) {
        this.adminProperties = adminProperties;

        nacosProps.setProperty(PropertyKeyConst.SERVER_ADDR, adminProperties.getMeta().getNacos().getAddr());
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
        nacosProps.setProperty(PropertyKeyConst.NAMESPACE, adminProperties.getMeta().getNacos().getNamespace());
    }

    /**
     * retrieve a specified config with Nacos SDK
     */
    @Override
    public CommonResponse retrieveConfig(String dataId, String group) {
        ConfigService configService;
        try {
            configService = NacosFactory.createConfigService(nacosProps);
        } catch (Exception e) {
            log.error("Create Nacos ConfigService error", e);
            return new CommonResponse("Create Nacos ConfigService error", e);
        }
        try {
            String configData = configService.getConfig(dataId, group, adminProperties.getConfig().getTimeoutMs());
            return new CommonResponse(configData);
        } catch (Exception e) {
            log.error("Get Nacos config error", e);
            return new CommonResponse("Get Nacos config error", e);
        }
    }

    /**
     * retrieve a list of configs with Nacos OpenAPI, because Nacos SDK doesn't support listing and fuzzy matching
     */
    @Override
    public String retrieveConfigs(Integer page, Integer size, String dataId, String group) {
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

        ResponseEntity<String> response = restTemplate.getForEntity(urlBuilder.toUriString(), String.class);
        return response.getBody();
    }

    /**
     * login if auth enabled and return accessToken
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
            return "";
        }
        return JSON.parseObject(loginResponse.getBody()).getString("accessToken");
    }
}
