package org.apache.eventmesh.runtime.boot;

public class RuntimeInstanceStarter {

    public static void main(String[] args) {
        // TODO:加载配置,从环境变量中拿到JobID,并去Admin获取Job配置
        // TODO:启动grpc server,连接META获取Admin地址,上报心跳
        // TODO:添加shutDownHook
    }
}
