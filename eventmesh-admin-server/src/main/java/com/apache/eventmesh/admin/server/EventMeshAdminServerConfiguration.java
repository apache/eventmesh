package com.apache.eventmesh.admin.server;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.config.Config;
import org.apache.eventmesh.common.config.ConfigFiled;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Config(prefix = "eventMesh.admin")
public class EventMeshAdminServerConfiguration extends CommonConfiguration {
    @ConfigFiled(field = "server.http.port")
    private int eventMeshHttpServerPort = 10000;

    @ConfigFiled(field = "server.gRPC.port")
    private int eventMeshGrpcServerPort = 10000;

    @ConfigFiled(field = "registry.plugin.server-addr", notEmpty = true)
    private String registryCenterAddr = "";

    @ConfigFiled(field = "registry.plugin.type", notEmpty = true)
    private String eventMeshRegistryPluginType = "nacos";

    @ConfigFiled(field = "registry.plugin.username")
    private String eventMeshRegistryPluginUsername = "";

    @ConfigFiled(field = "registry.plugin.password")
    private String eventMeshRegistryPluginPassword = "";
}
