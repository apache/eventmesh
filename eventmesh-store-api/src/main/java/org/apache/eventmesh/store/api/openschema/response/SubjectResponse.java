package org.apache.eventmesh.store.api.openschema.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SubjectResponse {
	
	  private String tenant;
	  private String namespace;
	  private String subject;
	  private String app;
	  private String description;
	  private String status;
	  private String compatibility;
	  private String coordinate;	  
	  private SchemaResponse schemaResponse;
	  
	  @JsonCreator
	  public SubjectResponse(@JsonProperty("tenant") String tenant,
	                @JsonProperty("namespace") String namespace,
	                @JsonProperty("subject") String subject,
	                @JsonProperty("app") String app,	                
	                @JsonProperty("description") String description,
	                @JsonProperty("status") String status,	                
	                @JsonProperty("compatibility") String compatibility,
	                @JsonProperty("coordinate") String coordinate,	                
	                @JsonProperty("schema") SchemaResponse schemaResponse) {
		this.tenant = tenant;
		this.namespace = namespace;
		this.subject = subject;
		this.app = app;
		this.description = description;
		this.status = status;
		this.compatibility = compatibility;
	    this.coordinate = coordinate;	    
	    this.schemaResponse = schemaResponse;
	  }	  
	  
	  @JsonProperty("tenant")
	  public String getTenant() {
	    return this.tenant;
	  }

	  @JsonProperty("tenant")
	  public void setTenant(String tenant) {
	    this.tenant = tenant;
	  }

	  @JsonProperty("namespace")
	  public String getNamespace() {
	    return this.namespace;
	  }

	  @JsonProperty("namespace")
	  public void setNamespace(String namespace) {
	    this.namespace = namespace;
	  }
	  
	  @JsonProperty("subject")
	  public String getSubject() {
	    return subject;
	  }

	  @JsonProperty("subject")
	  public void setSubject(String subject) {
	    this.subject = subject;
	  }

	  
	  @JsonProperty("app")
	  public String getApp() {
	    return app;
	  }

	  @JsonProperty("app")
	  public void setApp(String app) {
	    this.app = app;
	  }

	  @JsonProperty("description")
	  public String getDescription() {
	    return description;
	  }

	  @JsonProperty("description")
	  public void setDescription(String description) {
	    this.description = description;
	  }

	  @JsonProperty("status")
	  public String getStatus() {
	    return status;
	  }

	  @JsonProperty("status")
	  public void setStatus(String status) {
	    this.status = status;
	  }

	  @JsonProperty("compatibility")
	  public String getCompatibility() {
	    return compatibility;
	  }

	  @JsonProperty("compatibility")
	  public void setCompatibility(String compatibility) {
	    this.compatibility = compatibility;
	  }	  	  	  

	  @JsonProperty("coordinate")
	  public String getCoordinate() {
	    return this.coordinate;
	  }

	  @JsonProperty("coordinate")
	  public void setCoordinate(String coordinate) {
	    this.coordinate = coordinate;
	  }
	  
	  @JsonProperty("schema")
	  public SchemaResponse getSchemaResponse() {
	    return schemaResponse;
	  }

	  @JsonProperty("schema")
	  public void setSchemaResponse(SchemaResponse schemaResponse) {
	    this.schemaResponse = schemaResponse;
	  }	
	  
	  @Override
	  public String toString() {
	    StringBuilder sb = new StringBuilder();	    
	    sb.append("SchemaSubjectResponse {tenant=" + this.tenant + ",");
	    sb.append("namespace=" + this.namespace + ",");
	    sb.append("subject=" + this.subject + ",");
	    sb.append("app=" + this.app + ",");
	    sb.append("description=" + this.description + ",");
	    sb.append("status=" + this.status + ",");
	    sb.append("compatibility=" + this.compatibility + ",");
	    sb.append("coordinate=" + this.coordinate + "}");
	    return sb.toString();
	  }
	  
}
