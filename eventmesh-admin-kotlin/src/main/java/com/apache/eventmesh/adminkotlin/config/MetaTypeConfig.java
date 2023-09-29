package com.apache.eventmesh.adminkotlin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.apache.eventmesh.adminkotlin.service.ConnectionService;
import com.apache.eventmesh.adminkotlin.service.impl.EtcdConnectionService;
import com.apache.eventmesh.adminkotlin.service.impl.NacosConnectionService;

/**
 * Use different registry SDK depending on the configured meta type
 */
@Configuration
public class MetaTypeConfig {

    private final AdminProperties adminProperties;

    public MetaTypeConfig(AdminProperties adminProperties) {
        this.adminProperties = adminProperties;
    }

    @Bean
    public ConnectionService connectionService() {
        switch (adminProperties.getMeta().getType()) {
            case "nacos":
                return new NacosConnectionService();
            case "etcd":
                return new EtcdConnectionService();
            default:
                throw new IllegalArgumentException("Unsupported eventmesh meta type: " + adminProperties.getMeta().getType());
        }
    }
}