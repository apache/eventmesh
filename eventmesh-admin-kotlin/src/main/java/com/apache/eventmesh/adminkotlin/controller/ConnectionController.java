package com.apache.eventmesh.adminkotlin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.apache.eventmesh.adminkotlin.service.ConnectionService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class ConnectionController {

    @Autowired
    public ConnectionService connectionService;

    /**
     * Query Connection List
     * <p>
     * The subscription information of SourceConnector and SinkConnector reported by EventMesh Runtime to the meta,
     * containing the configuration of each connection.
     *
     * @param page the page number
     * @param size the page size
     * @return A paged list of connection configuration and total number of pages
     */
    @GetMapping("/connections")
    public String listConnections(@RequestParam("page") Integer page, @RequestParam("size") String size) {
        return "connections list";
    }

}
