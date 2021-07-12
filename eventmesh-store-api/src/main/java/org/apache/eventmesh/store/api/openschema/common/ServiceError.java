package org.apache.eventmesh.store.api.openschema.common;

public enum ServiceError {
    /**
     * 
     */
    ERR_SCHEMA_EMPTY("442", 442, "schema info cannot be empty"),

    /**
     * 
     */
    ERR_PERM_NO_AUTH("401", 401, "40101 - no permission to access"),

    /**
     * 
     */
    ERR_SCHEMA_INVALID("404", 404, "40401 - schema information does not exist in our record"),
    
    /**
     * 
     */
    ERR_SCHEMA_VERSION_INVALID("404", 404, "40402 - schema version does not exist in our record"),
    
    /**
     * 
     */
    ERR_SCHEMA_FORMAT_INVALID("422", 422, "40401 - schema format is invalid"),
    
    /**
     * 
     */
    ERR_SCHEMA_VERSION_FORMAT_INVALID("422", 422, "40402 - schema version format is invalid"),
    
    /**
     * 
     */
    ERR_SERVER_ERROR("500", 500, "I50001 -  Internal Server Error"),

    /**
     * resource level error definitions
     */
    ERR_RES_NOT_FOUND("500", 500, "50002 - Request Timeout");

    private String status;

    private int errorCode;

    private String errorMessage;

    ServiceError(String status, int errorCode, String errorMessage) {
        this.status = status;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
    
    public String getStatus() {
        return this.status;
    }

    public int getErrorCode() {
        return this.errorCode;
    }
    
    public String getMessage() {
        return this.errorMessage;
    }
}
