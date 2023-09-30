package com.apache.eventmesh.adminkotlin.model;

public class SubscriptionInfo {
    // TODO
    private String configData;
    private String errorMessage;

    public SubscriptionInfo(String configData) {
        this.configData = configData;
    }

    public SubscriptionInfo(String errorMessage, Exception exception) {
        this.errorMessage = errorMessage;
        if (exception != null) {
            this.errorMessage += ": " + exception.getMessage();
        }
    }

}
