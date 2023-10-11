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

package org.apache.eventmesh.api.acl;

import org.apache.eventmesh.api.exception.AclException;
import org.apache.eventmesh.spi.EventMeshExtensionType;
import org.apache.eventmesh.spi.EventMeshSPI;

/**
 * AclService
 */
@EventMeshSPI(eventMeshExtensionType = EventMeshExtensionType.SECURITY)
public interface AclService {

    void init() throws AclException;

    void start() throws AclException;

    void shutdown() throws AclException;

    void doAclCheckInConnect(AclProperties aclProperties) throws AclException;

    void doAclCheckInHeartbeat(AclProperties aclProperties) throws AclException;

    void doAclCheckInSend(AclProperties aclProperties) throws AclException;

    void doAclCheckInReceive(AclProperties aclProperties) throws AclException;

}
