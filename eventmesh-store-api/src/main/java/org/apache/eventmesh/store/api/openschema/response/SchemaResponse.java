package org.apache.eventmesh.store.api.openschema.response;

import org.apache.eventmesh.store.api.openschema.request.BaseRequest;
import org.apache.eventmesh.store.api.openschema.response.BaseResponse;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SchemaResponse extends BaseResponse{
		  
	  private String name;
	  private String comment;
	  private String serialization;
	  private String schemaType;
	  private String schemaDefinition;
	  private String validator;
	  private String version;	   
	  
	  @JsonCreator
	  public SchemaResponse(@JsonProperty("name") String name,
	                @JsonProperty("comment") String comment,
	                @JsonProperty("serialization") String serialization,
	                @JsonProperty("schemaType") String schemaType,	                
	                @JsonProperty("schemaDefinition") String schemaDefinition,
	                @JsonProperty("validator") String validator,	                
	                @JsonProperty("version") String version) {		
		this.name = name;
		this.comment = comment;
		this.serialization = serialization;
		this.schemaType = schemaType;
		this.schemaDefinition = schemaDefinition;
		this.validator = validator;
		this.version = version;	    
	  }	  
	  
	  @JsonProperty("name")
	  public String getName() {
	    return this.name;
	  }

	  @JsonProperty("name")
	  public void setName(String name) {
	    this.name = name;
	  }

	  @JsonProperty("comment")
	  public String getComment() {
	    return this.comment;
	  }

	  @JsonProperty("comment")
	  public void setComment(String comment) {
	    this.comment = comment;
	  }
	  
	  @JsonProperty("serialization")
	  public String getSerialization() {
	    return serialization;
	  }

	  @JsonProperty("serialization")
	  public void setSerialization(String serialization) {
	    this.serialization = serialization;
	  }

	  
	  @JsonProperty("schemaType")
	  public String getSchemaType() {
	    return schemaType;
	  }

	  @JsonProperty("schemaType")
	  public void setSchemaType(String schemaType) {
	    this.schemaType = schemaType;
	  }

	  @JsonProperty("schemaDefinition")
	  public String getSchemaDefinition() {
	    return schemaDefinition;
	  }

	  @JsonProperty("schemaDefinition")
	  public void setSchemaDefinition(String schemaDefinition) {
	    this.schemaDefinition = schemaDefinition;
	  }

	  @JsonProperty("validator")
	  public String getValidator() {
	    return validator;
	  }

	  @JsonProperty("validator")
	  public void setValidator(String validator) {
	    this.validator = validator;
	  }

	  @JsonProperty("version")
	  public String getVersion() {
	    return version;
	  }

	  @JsonProperty("version")
	  public void setVersion(String version) {
	    this.version = version;
	  }	  	  	  
	  
	  @Override
	  public String toString() {
	    StringBuilder sb = new StringBuilder();	    	    
	    sb.append("SchemaRequest {name=" + this.name + ",");
	    sb.append("comment=" + this.comment + ",");
	    sb.append("serialization=" + this.serialization + ",");
	    sb.append("schemaType=" + this.schemaType + ",");
	    sb.append("schemaDefinition=" + this.schemaDefinition + ",");
	    sb.append("validator=" + this.validator + ",");
	    sb.append("version=" + this.version + "}");
	    return sb.toString();
	  }
}
