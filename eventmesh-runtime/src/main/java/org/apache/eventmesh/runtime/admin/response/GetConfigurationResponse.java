package org.apache.eventmesh.runtime.admin.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class GetConfigurationResponse {
    public String eventMeshEnv;
    public String eventMeshIDC;
    public String eventMeshCluster;
    public String eventMeshServerIp;
    public boolean eventMeshServerSecurityEnable;
    public boolean eventMeshServerRegistryEnable;
    public String eventMeshName;
    public String sysID;
    public String eventMeshWebhookOrigin;
    public String namesrvAddr;

    //    public String eventMeshConnectorPluginType;
    //    public String eventMeshSecurityPluginType;
    //    public String eventMeshRegistryPluginType;
    //    public String eventMeshRegistryPluginUsername = "";
    //    public String eventMeshRegistryPluginPassword = "";
    //    public Integer eventMeshRegisterIntervalInMills;
    //    public Integer eventMeshFetchRegistryAddrInterval;
    //    public List<String> eventMeshMetricsPluginType;
    //    public String eventMeshTracePluginType;
    //    public boolean eventMeshServerTraceEnable;

    @JsonCreator
    public GetConfigurationResponse(
            @JsonProperty("sysID") String sysID,
            @JsonProperty("namesrvAddr") String namesrvAddr,
            @JsonProperty("eventMeshEnv") String eventMeshEnv,
            @JsonProperty("eventMeshIDC") String eventMeshIDC,
            @JsonProperty("eventMeshCluster") String eventMeshCluster,
            @JsonProperty("eventMeshServerIp") String eventMeshServerIp,
            @JsonProperty("eventMeshName") String eventMeshName,
            @JsonProperty("eventMeshWebhookOrigin") String eventMeshWebhookOrigin,
            @JsonProperty("eventMeshServerSecurityEnable") boolean eventMeshServerSecurityEnable,
            @JsonProperty("eventMeshServerRegistryEnable") boolean eventMeshServerRegistryEnable
    ) {
        super();
        this.sysID = sysID;
        this.namesrvAddr = namesrvAddr;
        this.eventMeshEnv = eventMeshEnv;
        this.eventMeshIDC = eventMeshIDC;
        this.eventMeshCluster = eventMeshCluster;
        this.eventMeshServerIp = eventMeshServerIp;
        this.eventMeshName = eventMeshName;
        this.eventMeshWebhookOrigin = eventMeshWebhookOrigin;
        this.eventMeshServerSecurityEnable = eventMeshServerSecurityEnable;
        this.eventMeshServerRegistryEnable = eventMeshServerRegistryEnable;
    }
}
