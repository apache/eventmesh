package com.apache.eventmesh.adminkotlin.service.impl;

import org.springframework.stereotype.Service;

import com.apache.eventmesh.adminkotlin.service.ConnectionService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class EtcdConnectionService implements ConnectionService {

    @Override
    public String listConnections(Integer page, String size) {
        return "etcd";
    }
}
