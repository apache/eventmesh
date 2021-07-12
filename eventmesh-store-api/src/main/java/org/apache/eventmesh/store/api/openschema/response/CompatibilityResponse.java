package org.apache.eventmesh.store.api.openschema.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CompatibilityResponse {
	
	  private String compatibility;	  

	  @JsonProperty("compatibility")
	  public String getCompatibility() {
	    return compatibility;
	  }

	  @JsonProperty("compatibility")
	  public void setCompatibility(String compatibility) {
	    this.compatibility = compatibility;
	  }
	  
}
