package org.apache.eventmesh.runtime;

import org.apache.eventmesh.common.config.Config;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Config
public class RuntimeInstanceConfig {

        private String metaServerAddr;

        private String metaStoragePluginType;

        private String runtimeInstanceId;

        private String runtimeInstanceName;

        private String runtimeInstanceDesc;

        private String runtimeInstanceType;

        private String runtimeInstanceVersion;

        private String runtimeInstanceConfig;

        private String runtimeInstanceStatus;

}
