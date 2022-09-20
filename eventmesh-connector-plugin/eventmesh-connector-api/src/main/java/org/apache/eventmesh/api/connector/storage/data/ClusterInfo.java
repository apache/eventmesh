package org.apache.eventmesh.api.connector.storage.data;

import lombok.Data;

@Data
public class ClusterInfo {

    private String storageType;

    private String clusterName;

    private boolean isCreateTopic = false;

    private boolean isCreateConsumerGroupInfo = false;

    private boolean isCreateMessageId = false;

    private String serviceUrl;
}
