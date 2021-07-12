package org.apache.eventmesh.store.api.openschema.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BaseResponse {
	
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
