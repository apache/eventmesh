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
