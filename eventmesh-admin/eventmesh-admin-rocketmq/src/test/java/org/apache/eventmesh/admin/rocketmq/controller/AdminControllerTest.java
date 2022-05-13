package org.apache.eventmesh.admin.rocketmq.controller;

import com.sun.net.httpserver.HttpServer;
import junit.framework.TestCase;

import java.io.IOException;
import java.net.InetSocketAddress;

public class AdminControllerTest extends TestCase {


    public AdminControllerTest() throws IOException {

        HttpServer server = HttpServer.create(new InetSocketAddress(10106), 0);

        AdminController adminController = new AdminController();
        adminController.run(server);
    }


}
