package com.apache.eventmesh.admin.server;

import com.apache.eventmesh.admin.server.constatns.AdminServerConstants;
import org.apache.eventmesh.common.config.ConfigService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ExampleAdminServer {
    public static void main(String[] args) throws Exception {
        ConfigService.getInstance().setConfigPath(AdminServerConstants.EVENTMESH_CONF_HOME).setRootConfig(AdminServerConstants.EVENTMESH_CONF_FILE);
        SpringApplication.run(ExampleAdminServer.class);
    }
}
