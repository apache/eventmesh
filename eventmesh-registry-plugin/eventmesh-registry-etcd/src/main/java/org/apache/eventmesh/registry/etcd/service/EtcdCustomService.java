package org.apache.eventmesh.registry.etcd.service;

import org.apache.eventmesh.api.exception.RegistryException;
import org.apache.eventmesh.api.registry.bo.EventMeshServicePubTopicInfo;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.registry.etcd.constant.EtcdConstant;

import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.options.GetOption;

public class EtcdCustomService extends EtcdRegistryService {

    private static final String KEY_PREFIX = "eventMesh" + EtcdConstant.KEY_SEPARATOR;
    private static final String KEY_APP = "app";
    private static final String KEY_SERVICE = "service";
    private static final Logger logger = LoggerFactory.getLogger(EtcdCustomService.class);

    public List<EventMeshServicePubTopicInfo> findEventMeshServicePubTopicInfos() throws RegistryException {

        Client client = getEtcdClient();
        String keyPrefix = KEY_PREFIX + KEY_SERVICE + EtcdConstant.KEY_SEPARATOR;
        List<KeyValue> keyValues = null;
        try {
            List<EventMeshServicePubTopicInfo> eventMeshServicePubTopicInfoList = new ArrayList<>();
            ByteSequence keyByteSequence = ByteSequence.from(keyPrefix.getBytes(Constants.DEFAULT_CHARSET));
            GetOption getOption = GetOption.newBuilder().withPrefix(keyByteSequence).build();
            keyValues = client.getKVClient().get(keyByteSequence, getOption).get().getKvs();


            if (CollectionUtils.isNotEmpty(keyValues)) {
                for (KeyValue kv : keyValues) {
                    EventMeshServicePubTopicInfo eventMeshServicePubTopicInfo =
                        JsonUtils.parseObject(new String(kv.getValue().getBytes(), Constants.DEFAULT_CHARSET), EventMeshServicePubTopicInfo.class);
                    eventMeshServicePubTopicInfoList.add(eventMeshServicePubTopicInfo);
                }
                return eventMeshServicePubTopicInfoList;
            }
        } catch (Exception e) {
            logger.error("[EtcdRegistryService][findEventMeshServicePubTopicInfos] error", e);
            throw new RegistryException(e.getMessage());
        }

        return null;
    }


}
