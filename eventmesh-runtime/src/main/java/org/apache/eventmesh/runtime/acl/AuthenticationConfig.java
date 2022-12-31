package org.apache.eventmesh.runtime.acl;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import lombok.Data;

@Data
public class AuthenticationConfig {
	
	private Set<String> exemptionUrl;
	
	private Map<String,UserAuthenticationConfig> userConfig = new HashMap<>();
	
	
	
	@Data
	public static class UserAuthenticationConfig{
		
		private  String password;
		
		private  String address;
		
		private Map<String,String> topic;
	}
}
