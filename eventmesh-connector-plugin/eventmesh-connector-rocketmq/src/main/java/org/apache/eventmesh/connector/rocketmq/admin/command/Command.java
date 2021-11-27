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

package org.apache.eventmesh.connector.rocketmq.admin.command;

import org.apache.eventmesh.connector.rocketmq.common.EventMeshConstants;
import org.apache.eventmesh.connector.rocketmq.config.ClientConfiguration;
import org.apache.eventmesh.connector.rocketmq.config.ConfigurationWrapper;

import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;

import java.io.File;
import java.util.UUID;

public abstract class Command {
    protected DefaultMQAdminExt adminExt;

    protected String nameServerAddr;
    protected String clusterName;

    public void init() {
        ConfigurationWrapper configurationWrapper =
                new ConfigurationWrapper(EventMeshConstants.EVENTMESH_CONF_HOME
                        + File.separator
                        + EventMeshConstants.EVENTMESH_CONF_FILE, false);
        final ClientConfiguration clientConfiguration =
            new ClientConfiguration(configurationWrapper);
        clientConfiguration.init();

        nameServerAddr = clientConfiguration.namesrvAddr;
        clusterName = clientConfiguration.clusterName;
        String accessKey = clientConfiguration.accessKey;
        String secretKey = clientConfiguration.secretKey;

        RPCHook rpcHook = new AclClientRPCHook(new SessionCredentials(accessKey, secretKey));
        adminExt = new DefaultMQAdminExt(rpcHook);
        String groupId = UUID.randomUUID().toString();
        adminExt.setAdminExtGroup("admin_ext_group-" + groupId);
        adminExt.setNamesrvAddr(nameServerAddr);
    }

    public abstract void execute() throws Exception;
}
