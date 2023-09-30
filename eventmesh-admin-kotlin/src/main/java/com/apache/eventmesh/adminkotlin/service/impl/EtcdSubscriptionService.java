package com.apache.eventmesh.adminkotlin.service.impl;

import org.springframework.stereotype.Service;

import com.apache.eventmesh.adminkotlin.service.SubscriptionService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class EtcdSubscriptionService implements SubscriptionService {

    @Override
    public String retrieveConfig(String dataId, String group) {
        return null;
    }
}
