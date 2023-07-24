package org.apache.eventmesh.connector.redis.config;

import org.apache.eventmesh.openconnect.api.config.Config;

import lombok.Data;

@Data
public class RedisServerConfig extends Config {

    private boolean sourceEnable;

    private boolean sinkEnable;

}