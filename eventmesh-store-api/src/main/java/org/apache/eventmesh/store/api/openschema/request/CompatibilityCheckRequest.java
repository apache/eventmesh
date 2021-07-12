package org.apache.eventmesh.store.api.openschema.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CompatibilityCheckRequest {
	
	private String schema;
	
	@JsonProperty("schema")
	public String getSchema() {
	  return this.schema;
	}

	@JsonProperty("schema")
	public void setSchema(String schema) {
	  this.schema = schema;
	}
	
}
