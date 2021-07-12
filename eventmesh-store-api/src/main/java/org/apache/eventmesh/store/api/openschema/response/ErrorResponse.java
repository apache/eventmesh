package org.apache.eventmesh.store.api.openschema.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Generic JSON error message.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ErrorResponse {
	
	  private int errorCode;
	  private String errorMessage;

	  public ErrorResponse(@JsonProperty("error_code") int errorCode,
	                       @JsonProperty("error_message") String errorMessage) {
	    this.errorCode = errorCode;
	    this.errorMessage = errorMessage;
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
	  
}
