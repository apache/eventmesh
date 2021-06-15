package org.apache.eventmesh.common.protocol;

public enum SubscriptionMode {

    /**
     * broadcast
     */
    BROADCASTING("BROADCASTING"),
    /**
     * clustering
     */
    CLUSTERING("CLUSTERING");

    private String mode;

    SubscriptionMode(String mode) {
        this.mode = mode;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

}
