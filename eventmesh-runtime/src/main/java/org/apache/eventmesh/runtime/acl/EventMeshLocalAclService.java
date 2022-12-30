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
package org.apache.eventmesh.runtime.acl;

import org.apache.eventmesh.runtime.acl.AuthenticationConfig.UserAuthenticationConfig;

import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

@Slf4j(topic = "acl")
public class EventMeshLocalAclService implements EventMeshAclServcie{

	private static AuthResult AUTH_RESULT = AuthResult.builder().success(true).build();
	
	
	private AclConfig aclConfig = new AclConfig();
	
	private AuthenticationConfig authenticationConfig;
	
	public EventMeshLocalAclService(AclConfig aclConfig) {
		if(!aclConfig.isAclEnabled()) {
			return;
		}
		
		if(Objects.nonNull(aclConfig.getRemoteAddress())) {
			
		} else {
			
		}
	}
	
	@Override
	public AuthResult auth(AuthTO authTO) {
		if(!aclConfig.isAclEnabled()) {
			return AUTH_RESULT;
		}
		
		if(!Objects.equals(aclConfig.getAclAuthType(), "password")) {
			return AUTH_RESULT;
		}
		
		UserAuthenticationConfig userAuthenticationConfig = authenticationConfig.getUserConfig().get(authTO.getAccount());
		if(Objects.isNull(userAuthenticationConfig) || !Objects.equals(userAuthenticationConfig.getPassword(), authTO.getPassword())) {
			log.warn("account and password mismatching, account is {} , address is {}", authTO.getAccount(), authTO.getAddress());
			return  AuthResult.builder().success(false).message("account and password mismatching").build();
		}
		// if ip
		
		
		return AUTH_RESULT;
	}

	@Override
	public AuthResult validate(AuthTO authTO) {
		
		if(!aclConfig.isAclEnabled()) {
			return AUTH_RESULT;
		}
		
		if(authenticationConfig.getExemptionUrl().contains(authTO.getInterfaces())) {
			return AUTH_RESULT;
		}
		
		if(Objects.equals(aclConfig.getAclAuthType(), "password")) {
			return AUTH_RESULT;
		}
		
		UserAuthenticationConfig userAuthenticationConfig = authenticationConfig.getUserConfig().get(authTO.getAccount());
		if(Objects.isNull(userAuthenticationConfig)) {
			log.warn("account and password mismatching, account is {} , address is {}", authTO.getAccount(), authTO.getAddress());
			return  AuthResult.builder().success(false).message("account and password mismatching").build();
		}
		
		// if ip
		
		
		return null;
	}

}
