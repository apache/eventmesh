package org.apache.eventmesh.webhook.api;


import java.util.*;

/**
 * 厂商信息汇总实体
 */
public class ManufacturerObject {

    private Set<String> manufacturerList = new HashSet<>();

    private Map<String , List<String>> manufacturerEventMap = new HashMap<>();


    public Set<String> getManufacturerList() {
        return manufacturerList;
    }

    public Set<String> addManufacturer(String manufacturer) {
        manufacturerList.add(manufacturer);
        return manufacturerList;
    }

    public Set<String> removeManufacturer(String manufacturer) {
        manufacturerList.remove(manufacturer);
        return manufacturerList;
    }

    public Map<String, List<String>> getManufacturerEventMap() {
        return manufacturerEventMap;
    }

    public void setManufacturerEventMap(Map<String, List<String>> manufacturerEventMap) {
        this.manufacturerEventMap = manufacturerEventMap;
    }

    public List<String> getManufacturerEvents(String manufacturerName) {
        if (!manufacturerEventMap.containsKey(manufacturerName)) {
            List<String> m = new ArrayList<>();
            manufacturerEventMap.put(manufacturerName, m);
            return m;
        }
        return manufacturerEventMap.get(manufacturerName);
    }
}
