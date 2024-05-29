package org.apache.eventmesh.common.remote.response;

public class FailResponse extends BaseRemoteResponse {
    public static FailResponse build(int errorCode, String msg) {
        FailResponse response = new FailResponse();
        response.setErrorCode(errorCode);
        response.setDesc(msg);
        response.setSuccess(false);
        return response;
    }


    /**
     * build an error response.
     *
     * @param exception exception
     * @return response
     */
    public static FailResponse build(Throwable exception) {
        return build(BaseRemoteResponse.UNKNOWN, exception.getMessage());
    }
}
