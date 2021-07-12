package org.apache.eventmesh.store.api.openschema.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CompatibilityCheckResponse {
	
	  private boolean isCompatible;	  

	  @JsonProperty("is_compatible")
	  public boolean getIsCompatible() {
	    return isCompatible;
	  }

	  @JsonProperty("is_compatible")
	  public void setIsCompatible(boolean isCompatible) {
	    this.isCompatible = isCompatible;
	  }
	  
}
