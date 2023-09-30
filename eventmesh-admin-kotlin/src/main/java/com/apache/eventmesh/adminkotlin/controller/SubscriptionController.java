package com.apache.eventmesh.adminkotlin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.apache.eventmesh.adminkotlin.service.SubscriptionService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class SubscriptionController {

    @Autowired
    public SubscriptionService subscriptionService;

    @GetMapping("/subscription")
    public String listSubscriptions(@RequestParam("page") Integer page, @RequestParam("size") String size, String dataId, String group) {
        return subscriptionService.retrieveConfig(dataId, group);
    }

}
