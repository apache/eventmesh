package org.apache.eventmesh.runtime;

import org.apache.eventmesh.common.config.Config;
import org.apache.eventmesh.common.enums.ComponentType;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Config(path = "classPath://runtime.yaml")
public class RuntimeInstanceConfig {

        private String registryServerAddr;

        private String registryPluginType;

        private String storagePluginType;

        private String adminServiceName;

        private String adminServerAddr;

        private ComponentType componentType;

        private String runtimeInstanceId;

        private String runtimeInstanceName;

        private String runtimeInstanceDesc;

        private String runtimeInstanceVersion;

        private String runtimeInstanceConfig;

        private String runtimeInstanceStatus;

}
