package com.apache.eventmesh.adminkotlin.config;

import static com.apache.eventmesh.adminkotlin.config.Constants.ADMIN_PROPS_PREFIX;
import static com.apache.eventmesh.adminkotlin.config.Constants.META_TYPE_NACOS;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = ADMIN_PROPS_PREFIX)
public class AdminProperties {

    private MetaProperties meta = new MetaProperties();

    private ConfigProperties config = new ConfigProperties();

    @Data
    public static class MetaProperties {

        private String type = META_TYPE_NACOS;

        private NacosProperties nacos = new NacosProperties();

        private EtcdProperties etcd = new EtcdProperties();

        @Data
        public static class NacosProperties {

            private String addr = "127.0.0.1:8848";

            private boolean authEnabled;

            private String username;

            private String password;

            private String accessKey;

            private String secretKey;

            private String namespace = "";

        }

        @Data
        public static class EtcdProperties {

            private String addr;

        }
    }

    @Data
    public static class ConfigProperties {

        private int timeoutMs = 5000;

    }
}