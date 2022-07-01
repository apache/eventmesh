package org.apache.eventmesh.connector.dledger.config;

import org.apache.commons.lang3.StringUtils;

import lombok.Getter;

@Getter
public class DLedgerClientConfiguration {
    public static String KEYS_EVENTMESH_DLEDGER_GROUP = "eventMesh.server.dledger.group";
    public static String KEYS_EVENTMESH_DLEDGER_PEERS = "eventMesh.server.dledger.peers";
    public static String KEYS_EVENTMESH_DLEDGER_CLIENTPOOL_SIZE = "eventMesh.server.dledger.clientPool.size";

    private String group = "default";
    private String peers = "n0-localhost:20911";
    private int clientPoolSize = 8;

    public void init() {
        String groupStr = DLedgerConfigurationWrapper.getProp(KEYS_EVENTMESH_DLEDGER_GROUP);
        if (StringUtils.isNotBlank(groupStr)) {
            group = StringUtils.trim(groupStr);
        }

        String peersStr = DLedgerConfigurationWrapper.getProp(KEYS_EVENTMESH_DLEDGER_PEERS);
        if (StringUtils.isNotBlank(peersStr)) {
            peers = StringUtils.trim(peers);
        }

        String clientPoolSizeStr = DLedgerConfigurationWrapper.getProp(KEYS_EVENTMESH_DLEDGER_CLIENTPOOL_SIZE);
        if (StringUtils.isNumeric(clientPoolSizeStr)) {
            clientPoolSize = Integer.parseInt(clientPoolSizeStr);
        }
    }
}
