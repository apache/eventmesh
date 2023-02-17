package org.apache.eventmesh.api.acl;

import java.util.concurrent.ConcurrentHashMap;


public class AclProperties {

    private String clientIp;
    private String user;
    private String pwd;
    private String subsystem;
    private String topic;
    private Integer requestCode;
    private String requestURI;
    private String token;
    private ConcurrentHashMap<String,Object> extendedField;

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPwd() {
        return pwd;
    }

    public void setPwd(String pwd) {
        this.pwd = pwd;
    }

    public String getSubsystem() {
        return subsystem;
    }

    public void setSubsystem(String subsystem) {
        this.subsystem = subsystem;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public Integer getRequestCode() {
        return requestCode;
    }

    public void setRequestCode(Integer requestCode) {
        this.requestCode = requestCode;
    }

    public String getRequestURI() {
        return requestURI;
    }

    public void setRequestURI(String requestURI) {
        this.requestURI = requestURI;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public ConcurrentHashMap<String, Object> getExtendedField() {
        return extendedField;
    }

    public void setExtendedField(ConcurrentHashMap<String, Object> extendedField) {
        this.extendedField = extendedField;
    }
}
