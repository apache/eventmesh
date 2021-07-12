package org.apache.eventmesh.store.api.openschema.common;


import org.apache.eventmesh.store.api.openschema.response.ErrorResponse;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ServiceException extends Exception {
      private static final long serialVersionUID = 1L;
      
	  private int errorCode;
	  private String errorMessage;

	  //public ErrorResponse(@JsonProperty("error_code") int errorCode,
	  //                     @JsonProperty("error_message") String errorMessage) {
	  public ServiceException(ServiceError serviceError) {		  
		super(serviceError.getMessage());
	    this.errorCode = serviceError.getErrorCode();
	    this.errorMessage = serviceError.getMessage();
	  }

	  @JsonProperty("error_code")
	  public int getErrorCode() {
	    return errorCode;
	  }

	  @JsonProperty("error_code")
	  public void setErrorCode(int error_code) {
	    this.errorCode = error_code;
	  }

	  @JsonProperty("error_message")
	  public String getErrorMessage() {
	    return errorMessage;
	  }

	  @JsonProperty("error_message")
	  public void setErrorMessage(String errorMessage) {
	    this.errorMessage = errorMessage;
	  }
	  	  
	  public ErrorResponse getErrorResponse() {
	    /*StringBuilder sb = new StringBuilder();	    
	    sb.append("{error_code=" + this.errorCode + ",");	    
	    sb.append("error_message=" + this.errorMessage + "}");
	    */
	    return new ErrorResponse(this.errorCode, this.errorMessage);
	  }
}
