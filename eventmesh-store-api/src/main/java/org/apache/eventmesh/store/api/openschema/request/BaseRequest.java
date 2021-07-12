package org.apache.eventmesh.store.api.openschema.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BaseRequest {
	
	private String id;
	
	@JsonProperty("id")
	public String getId() {
	  return this.id;
	}

	@JsonProperty("id")
	public void setId(String id) {
	  this.id = id;
	}
	  
}
