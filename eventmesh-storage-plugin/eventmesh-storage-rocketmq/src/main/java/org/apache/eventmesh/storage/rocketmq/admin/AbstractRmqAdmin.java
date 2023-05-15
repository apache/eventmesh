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
