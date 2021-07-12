package org.apache.eventmesh.store.api.openschema.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfigUpdateRequest {

	  private String compatibilityLevel;

	 /* @ApiModelProperty(value = "Compatability Level",
	      allowableValues = "BACKWARD, BACKWARD_TRANSITIVE, FORWARD, FORWARD_TRANSITIVE, FULL, "
	          + "FULL_TRANSITIVE, NONE")*/
	  @JsonProperty("compatibility")
	  public String getCompatibilityLevel() {
	    return this.compatibilityLevel;
	  }
	  
	  @JsonProperty("compatibility")
	  public void setCompatibilityLevel(String compatibilityLevel) {
	    this.compatibilityLevel = compatibilityLevel;
	  }
	  
}
