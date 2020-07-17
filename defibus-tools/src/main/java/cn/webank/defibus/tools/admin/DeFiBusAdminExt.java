package cn.webank.defibus.tools.admin;

import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;

public class DeFiBusAdminExt extends DefaultMQAdminExt {
    public DeFiBusAdminExt(RPCHook rpcHook, long timeoutMillis) {
        super(rpcHook, timeoutMillis);
    }
}
