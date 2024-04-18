package com.apache.eventmesh.admin.server.registry;

import lombok.Data;

import java.util.Map;

@Data
public class EventMeshAdminServerRegisterInfo {
    private String eventMeshClusterName;
    private String eventMeshName;
    private String address;

    private Map<String, String> metadata;
}
