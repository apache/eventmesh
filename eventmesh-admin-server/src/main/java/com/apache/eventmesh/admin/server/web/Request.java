package com.apache.eventmesh.admin.server.web;

public class Request<T> {
    private String uid;
    private T data;

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
