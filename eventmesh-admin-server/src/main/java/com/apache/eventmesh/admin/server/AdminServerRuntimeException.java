package com.apache.eventmesh.admin.server;

import lombok.Getter;

public class AdminServerRuntimeException extends RuntimeException {
    @Getter
    private final int code;
    public AdminServerRuntimeException(int code, String message) {
        super(message);
        this.code = code;
    }
}
