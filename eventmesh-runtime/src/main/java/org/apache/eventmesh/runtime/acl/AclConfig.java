package org.apache.eventmesh.runtime.acl;

import lombok.Data;

@Data
public class AclConfig {

	private boolean aclEnabled = false;
	
	private String aclType;
	
	private String aclAuthType = "password";
	
	private String aclValidateType;
	
	private String remoteAddress;
	
	
}
