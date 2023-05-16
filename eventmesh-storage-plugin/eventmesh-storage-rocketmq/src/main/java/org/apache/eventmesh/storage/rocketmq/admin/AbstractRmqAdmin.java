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
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.eventmesh.storage.rocketmq.admin;

import org.apache.eventmesh.common.config.ConfigService;
import org.apache.eventmesh.storage.rocketmq.config.ClientConfiguration;

import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;

import java.util.UUID;

public abstract class AbstractRmqAdmin {
    private DefaultMQAdminExt adminExt;


    protected String nameServerAddr;

    protected String clusterName;

    protected DefaultMQAdminExt getAdminExt() throws Exception {
        ConfigService configService = ConfigService.getInstance();

        ClientConfiguration clientConfiguration = configService.buildConfigInstance(ClientConfiguration.class);

        nameServerAddr = clientConfiguration.getNamesrvAddr();
        clusterName = clientConfiguration.getClusterName();
        String accessKey = clientConfiguration.getAccessKey();
        String secretKey = clientConfiguration.getSecretKey();
        if (adminExt == null) {

            RPCHook rpcHook = new AclClientRPCHook(new SessionCredentials(accessKey, secretKey));
            adminExt = new DefaultMQAdminExt(rpcHook);
            String groupId = UUID.randomUUID().toString();
            adminExt.setAdminExtGroup("admin_ext_group-" + groupId);
            adminExt.setNamesrvAddr(nameServerAddr);
            adminExt.start();
        }

        return adminExt;
    }

    protected void shutdownExt() {
        adminExt.shutdown();
        adminExt = null;
    }

}
