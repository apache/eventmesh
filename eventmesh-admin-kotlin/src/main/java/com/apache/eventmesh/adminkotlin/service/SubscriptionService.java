package com.apache.eventmesh.adminkotlin.service;

import com.apache.eventmesh.adminkotlin.dto.CommonResponse;

public interface SubscriptionService {

    CommonResponse retrieveConfig(String dataId, String group);

    String retrieveConfigs(Integer page, Integer size, String dataId, String group);
}
