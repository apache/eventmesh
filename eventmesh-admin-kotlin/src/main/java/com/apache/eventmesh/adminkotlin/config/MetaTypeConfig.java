package com.apache.eventmesh.adminkotlin.config;

import static com.apache.eventmesh.adminkotlin.config.Constants.META_TYPE_ETCD;
import static com.apache.eventmesh.adminkotlin.config.Constants.META_TYPE_NACOS;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.apache.eventmesh.adminkotlin.service.ConnectionService;
import com.apache.eventmesh.adminkotlin.service.SubscriptionService;
import com.apache.eventmesh.adminkotlin.service.impl.EtcdConnectionService;
import com.apache.eventmesh.adminkotlin.service.impl.EtcdSubscriptionService;
import com.apache.eventmesh.adminkotlin.service.impl.NacosConnectionService;
import com.apache.eventmesh.adminkotlin.service.impl.NacosSubscriptionService;

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
            case META_TYPE_NACOS:
                return new NacosConnectionService(adminProperties);
            case META_TYPE_ETCD:
                return new EtcdConnectionService();
            default:
                throw new IllegalArgumentException("Unsupported eventmesh meta type: " + adminProperties.getMeta().getType());
        }
    }

    @Bean
    public SubscriptionService subscriptionService() {
        switch (adminProperties.getMeta().getType()) {
            case META_TYPE_NACOS:
                return new NacosSubscriptionService(adminProperties);
            case META_TYPE_ETCD:
                return new EtcdSubscriptionService();
            default:
                throw new IllegalArgumentException("Unsupported eventmesh meta type: " + adminProperties.getMeta().getType());
        }
    }
}