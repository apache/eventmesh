package org.apache.eventmesh.runtime;

import org.apache.eventmesh.common.config.Config;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Config(path = "classPath://runtime.yaml")
public class RuntimeInstanceConfig {

        private String registryServerAddr;

        private String registryPluginType;

        private String storagePluginType;

        private String runtimeInstanceId;

        private String runtimeInstanceName;

        private String runtimeInstanceDesc;

        private String runtimeInstanceType;

        private String runtimeInstanceVersion;

        private String runtimeInstanceConfig;

        private String runtimeInstanceStatus;

}
