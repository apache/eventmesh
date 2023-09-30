package com.apache.eventmesh.adminkotlin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.apache.eventmesh.adminkotlin.dto.CommonResponse;
import com.apache.eventmesh.adminkotlin.service.SubscriptionService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class SubscriptionController {

    @Autowired
    public SubscriptionService subscriptionService;

    /**
     * retrieve a specified config
     *
     * @param dataId nacos config data id (Exact Matching)
     * @param group  config group (Exact Matching)
     * @return the config content
     */
    @GetMapping("/subscription")
    public ResponseEntity<String> retrieveSubscription(@RequestParam("dataId") String dataId, @RequestParam("group") String group) {
        CommonResponse response = subscriptionService.retrieveConfig(dataId, group);
        if (response.getData() != null) {
            return ResponseEntity.ok(response.getData());
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response.getMessage());
        }
    }

    /**
     * retrieve a list of configs
     *
     * @param page page number
     * @param size page size
     * @param dataId nacos config data id (Fuzzy Matching)
     * @param group config group (Fuzzy Matching)
     * @return the config content and config properties
     */
    @GetMapping("/subscriptions")
    public ResponseEntity<String> listSubscriptions(
        @RequestParam(name = "page", defaultValue = "1") Integer page, @RequestParam(name = "size", defaultValue = "10") Integer size,
        @RequestParam(name = "dataId", defaultValue = "") String dataId, @RequestParam(name = "group", defaultValue = "") String group) {
        return ResponseEntity.ok(subscriptionService.retrieveConfigs(page, size, dataId, group));
    }

}
