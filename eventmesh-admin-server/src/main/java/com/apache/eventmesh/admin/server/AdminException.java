package com.apache.eventmesh.admin.server;

public class AdminException extends RuntimeException {
    public AdminException(String message) {
        super(message);
    }

    public AdminException(String message, Throwable cause) {
        super(message, cause);
    }
}
