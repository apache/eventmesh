package org.apache.eventmesh.client.grpc.util;

import io.cloudevents.SpecVersion;
import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.common.protocol.grpc.protos.RequestHeader;

public class EventMeshClientUtil {

    private final static String PROTOCOL_TYPE = "eventmeshmessage";
    private final static String PROTOCOL_DESC = "grpc";

    public static RequestHeader buildHeader(EventMeshGrpcClientConfig clientConfig) {
        RequestHeader header = RequestHeader.newBuilder()
            .setEnv(clientConfig.getEnv())
            .setIdc(clientConfig.getIdc())
            .setIp(clientConfig.getIp())
            .setPid(clientConfig.getPid())
            .setSys(clientConfig.getSys())
            .setLanguage(clientConfig.getLanguage())
            .setUsername(clientConfig.getUserName())
            .setPassword(clientConfig.getPassword())
            .setProtocolType(PROTOCOL_TYPE)
            .setProtocolDesc(PROTOCOL_DESC)
            // default CloudEvents version is V1
            .setProtocolVersion(SpecVersion.V1.toString())
            .build();
        return header;
    }
}
