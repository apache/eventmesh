package org.apache.eventmesh.connector.canal.source.position;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class TableFullPosition {
    private Map<String,Object> curPrimaryKeyCols = new LinkedHashMap<>();
    private Map<String,Object> minPrimaryKeyCols = new LinkedHashMap<>();
    private Map<String,Object> maxPrimaryKeyCols = new LinkedHashMap<>();
}
