package org.apache.eventmesh.store.api.openschema.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SubjectVersionResponse {
		  
	  private String subject;
	  private Integer version;

	  @JsonCreator
	  public SubjectVersionResponse(@JsonProperty("subject") String subject,
	                        @JsonProperty("version") Integer version) {
	    this.subject = subject;
	    this.version = version;
	  }

	  @JsonProperty("subject")
	  public String getSubject() {
	    return subject;
	  }

	  @JsonProperty("subject")
	  public void setSubject(String subject) {
	    this.subject = subject;
	  }

	  @JsonProperty("version")
	  public Integer getVersion() {
	    return this.version;
	  }

	  @JsonProperty("version")
	  public void setVersion(Integer version) {
	    this.version = version;
	  }
}
