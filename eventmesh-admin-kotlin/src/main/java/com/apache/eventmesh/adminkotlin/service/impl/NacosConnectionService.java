package com.apache.eventmesh.adminkotlin.service.impl;

import java.util.Properties;

import org.springframework.stereotype.Service;

import com.apache.eventmesh.adminkotlin.config.AdminProperties;
import com.apache.eventmesh.adminkotlin.service.ConnectionService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class NacosConnectionService implements ConnectionService {

    Properties properties = new Properties();

    public NacosConnectionService(AdminProperties adminProperties) {
        properties.setProperty("serverAddr", adminProperties.getMeta().getNacos().getAddr());
        properties.setProperty("username", adminProperties.getMeta().getNacos().getUsername());
        properties.setProperty("password", adminProperties.getMeta().getNacos().getPassword());
        properties.setProperty("namespace", adminProperties.getMeta().getNacos().getNamespace());
        properties.setProperty("timeoutMs", String.valueOf(adminProperties.getConfig().getTimeoutMs()));
    }

}
