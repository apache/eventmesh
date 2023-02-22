package org.apache.eventmesh.auth.token.impl;

import org.apache.eventmesh.api.acl.AclProperties;
import org.apache.eventmesh.api.acl.AclService;
import org.apache.eventmesh.api.exception.AclException;
import org.apache.eventmesh.auth.token.impl.auth.AuthTokenUtils;

public class AuthTokenServiceImpl implements AclService {

    @Override
    public void init() throws AclException {
    }

    @Override
    public void start() throws AclException {

    }

    @Override
    public void shutdown() throws AclException {

    }

    @Override
    public void doAclCheckInConnect(AclProperties aclProperties) throws AclException {
        AuthTokenUtils.authTokenByPublicKey(aclProperties);
    }

    @Override
    public void doAclCheckInHeartbeat(AclProperties aclProperties) throws AclException {

    }

    @Override
    public void doAclCheckInSend(AclProperties aclProperties) throws AclException {
        AuthTokenUtils.authTokenByPublicKey(aclProperties);
    }

    @Override
    public void doAclCheckInReceive(AclProperties aclProperties) throws AclException {
        AuthTokenUtils.authTokenByPublicKey(aclProperties);
    }
}
