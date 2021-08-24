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

package org.apache.eventmesh.store.h2.schema.domain;

public class Schema {
	  
	  private String id;
	  private String name;
	  private String comment;
	  private String serialization;
	  private String schemaType;
	  private String schemaDefinition;
	  private String validator;
	  private int version;	  
	  private String subjectName;
	  	  
	  public Schema(String id,
			  		String name,
	                String comment,
	                String serialization,
	                String schemaType,	                
	                String schemaDefinition,
	                String validator,	                
	                int version,
	                String subjectName) {
		this.name = name;
		this.comment = comment;
		this.serialization = serialization;
		this.schemaType = schemaType;
		this.schemaDefinition = schemaDefinition;
		this.validator = validator;
		this.version = version;	 
		this.subjectName = subjectName;
	  }	  	  	  
	  
	  public String getId() {
		  return this.id;
	  }
		  
	  public void setId(String id) {
		  this.id = id;
	  }
		  
	  public String getName() {
	    return this.name;
	  }
	  
	  public void setName(String name) {
	    this.name = name;
	  }
	  
	  public String getComment() {
	    return this.comment;
	  }
	  
	  public void setComment(String comment) {
	    this.comment = comment;
	  }
	  	  
	  public String getSerialization() {
	    return serialization;
	  }
	  
	  public void setSerialization(String serialization) {
	    this.serialization = serialization;
	  }
	  	  
	  public String getSchemaType() {
	    return schemaType;
	  }
	  
	  public void setSchemaType(String schemaType) {
	    this.schemaType = schemaType;
	  }
	  
	  public String getSchemaDefinition() {
	    return schemaDefinition;
	  }
	  
	  public void setSchemaDefinition(String schemaDefinition) {
	    this.schemaDefinition = schemaDefinition;
	  }
	  
	  public String getValidator() {
	    return validator;
	  }
	  
	  public void setValidator(String validator) {
	    this.validator = validator;
	  }
	  
	  public int getVersion() {
	    return version;
	  }
	  
	  public void setVersion(int version) {
	    this.version = version;
	  }	  	  	  
	  
	  public String getSubjectName() {
		return subjectName;
	  }
		  
	  public void setSubjectName(String subjectName) {
		this.subjectName = subjectName;
	  }
		  
	  @Override
	  public String toString() {
	    StringBuilder sb = new StringBuilder();	    	    
	    sb.append("Schema {name=" + this.name + ",");
	    sb.append("comment=" + this.comment + ",");
	    sb.append("id=" + this.id + ",");
	    sb.append("serialization=" + this.serialization + ",");
	    sb.append("schemaType=" + this.schemaType + ",");
	    sb.append("schemaDefinition=" + this.schemaDefinition + ",");
	    sb.append("validator=" + this.validator + ",");
	    sb.append("version=" + this.version + "}");
	    return sb.toString();
	  }

}
