package org.apache.eventmeth.protocol.http.utils;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.config.EventMeshVersion;
import org.apache.eventmesh.common.utils.ThreadUtil;

public class EventMeshUtils {

    public static String buildPushMsgSeqNo() {
        return StringUtils.rightPad(String.valueOf(System.currentTimeMillis()), 6) + String.valueOf(RandomStringUtils.randomNumeric(4));
    }

    public static String buildMeshClientID(String clientGroup, String meshCluster) {
        return StringUtils.trim(clientGroup)
                + "(" + StringUtils.trim(meshCluster) + ")"
                + "-" + EventMeshVersion.getCurrentVersionDesc()
                + "-" + ThreadUtil.getPID();
    }
}
