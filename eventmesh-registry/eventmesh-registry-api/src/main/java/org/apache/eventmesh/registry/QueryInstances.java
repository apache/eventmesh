package org.apache.eventmesh.registry;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class QueryInstances {
    private String serviceName;
    private boolean health;
    private Map<String,String> extFields = new HashMap<>();
}
