package com.apache.eventmesh.adminkotlin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "eventmesh")
public class AdminProperties {

    private MetaProperties meta = new MetaProperties();

    private ConfigProperties config = new ConfigProperties();

    @Data
    public static class MetaProperties {

        private String type;
        private NacosProperties nacos = new NacosProperties();
        private EtcdProperties etcd = new EtcdProperties();

        @Data
        public static class NacosProperties {
            private String addr;
            private String username;
            private String password;
        }

        @Data
        public static class EtcdProperties {
            private String addr;
        }
    }

    @Data
    public static class ConfigProperties {
        private int timeoutMillis;
    }
}