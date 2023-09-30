package com.apache.eventmesh.adminkotlin.service.impl;

import java.util.Properties;

import org.springframework.stereotype.Service;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.apache.eventmesh.adminkotlin.config.AdminProperties;
import com.apache.eventmesh.adminkotlin.service.SubscriptionService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class NacosSubscriptionService implements SubscriptionService {

    Properties properties = new Properties();

    public NacosSubscriptionService(AdminProperties adminProperties) {
        properties.setProperty("serverAddr", adminProperties.getMeta().getNacos().getAddr());
        properties.setProperty("username", adminProperties.getMeta().getNacos().getUsername());
        properties.setProperty("password", adminProperties.getMeta().getNacos().getPassword());
        properties.setProperty("namespace", adminProperties.getMeta().getNacos().getNamespace());
        properties.setProperty("timeoutMs", String.valueOf(adminProperties.getConfig().getTimeoutMs()));
    }

    @Override
    public String retrieveConfig(String dataId, String group) {
        ConfigService configService;
        try {
            configService = NacosFactory.createConfigService(properties);
        } catch (Exception e) {
            log.error("Create Nacos ConfigService error", e);
            return "Create Nacos ConfigService error: " + e.getMessage();
        }
        try {
            return configService.getConfig(dataId, group, Long.parseLong(properties.getProperty("timeoutMs")));
        } catch (Exception e) {
            log.error("Get Nacos Config error", e);
            return "Get Nacos Config error: " + e.getMessage();
        }
    }
}
