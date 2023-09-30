package com.apache.eventmesh.adminkotlin.dto;

import lombok.Data;

@Data
public class CommonResponse {

    private String data;

    private String message;

    public CommonResponse(String data) {
        this.data = data;
    }

    public CommonResponse(String message, Exception e) {
        this.message = message;
        if (e != null) {
            this.message += ": " + e.getMessage();
        }
    }
}
