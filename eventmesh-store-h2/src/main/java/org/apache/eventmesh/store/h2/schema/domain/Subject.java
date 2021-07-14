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

public class Subject {
	  	  
	  private String tenant;
	  private String namespace;
	  private String name;
	  private String app;
	  private String description;
	  private String status;
	  private String compatibility;
	  private String coordinate;
	  	  
	  public Subject(String name,
			        String tenant,
	                String namespace,	                
	                String app,	                
	                String description,
	                String status,	                
	                String compatibility,
	                String coordinate) {
		this.name = name;
		this.tenant = tenant;
		this.namespace = namespace;		
		this.app = app;
		this.description = description;
		this.status = status;
		this.compatibility = compatibility;
	    this.coordinate = coordinate;	    
	  }	  
	  
	  /*public String getId() {
		  return this.id;
	  }
		  
	  public void setId(String id) {
		  this.id = id;
	  }*/
	  	  
	  public String getTenant() {
	    return this.tenant;
	  }
	 
	  public void setTenant(String tenant) {
	    this.tenant = tenant;
	  }
	  
	  public String getNamespace() {
	    return this.namespace;
	  }
	  
	  public void setNamespace(String namespace) {
	    this.namespace = namespace;
	  }
	  	  	  
	  public String getName() {
	    return name;
	  }
	  
	  public void setName(String name) {
	    this.name = name;
	  }
	  	 
	  public String getApp() {
	    return app;
	  }
	  
	  public void setApp(String app) {
	    this.app = app;
	  }
	  
	  public String getDescription() {
	    return description;
	  }
	  
	  public void setDescription(String description) {
	    this.description = description;
	  }
	  
	  public String getStatus() {
	    return status;
	  }
	  
	  public void setStatus(String status) {
	    this.status = status;
	  }
	  
	  public String getCompatibility() {
	    return compatibility;
	  }
	  
	  public void setCompatibility(String compatibility) {
	    this.compatibility = compatibility;
	  }	  	  	  
	  
	  public String getCoordinate() {
	    return this.coordinate;
	  }
	  
	  public void setCoordinate(String coordinate) {
	    this.coordinate = coordinate;
	  }
	  
	  @Override
	  public String toString() {
	    StringBuilder sb = new StringBuilder();	    
	    sb.append("Subject {tenant=" + this.tenant + ",");	    
	    sb.append("namespace=" + this.namespace + ",");
	    sb.append("name=" + this.name + ",");
	    sb.append("app=" + this.app + ",");
	    sb.append("description=" + this.description + ",");
	    sb.append("status=" + this.status + ",");
	    sb.append("compatibility=" + this.compatibility + ",");
	    sb.append("coordinate=" + this.coordinate + "}");
	    return sb.toString();
	  }

}
