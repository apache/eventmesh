/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
