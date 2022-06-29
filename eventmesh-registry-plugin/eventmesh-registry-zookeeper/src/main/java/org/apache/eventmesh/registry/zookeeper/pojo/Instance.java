package org.apache.eventmesh.registry.zookeeper.pojo;


import java.io.Serializable;
import java.util.Map;

public class Instance implements Serializable {

    private static final long serialVersionUID = -7953085707514834697L;

    private String ip;

    private int port;

    private Map<String, Map<String, Integer>> instanceNumMap;

    private Map<String, String> metaData;

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public Map<String, Map<String, Integer>> getInstanceNumMap() {
        return instanceNumMap;
    }

    public void setInstanceNumMap(Map<String, Map<String, Integer>> instanceNumMap) {
        this.instanceNumMap = instanceNumMap;
    }

    public Map<String, String> getMetaData() {
        return metaData;
    }

    public void setMetaData(Map<String, String> metaData) {
        this.metaData = metaData;
    }
}
