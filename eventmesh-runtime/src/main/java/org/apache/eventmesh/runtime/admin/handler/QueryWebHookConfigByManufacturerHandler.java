package org.apache.eventmesh.runtime.admin.handler;

import org.apache.eventmesh.admin.rocketmq.util.JsonUtils;
import org.apache.eventmesh.admin.rocketmq.util.NetUtils;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.webhook.admin.AdminWebHookConfigOperationManage;
import org.apache.eventmesh.webhook.api.WebHookConfig;
import org.apache.eventmesh.webhook.api.WebHookConfigOperation;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class QueryWebHookConfigByManufacturerHandler implements HttpHandler {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        httpExchange.sendResponseHeaders(200, 0);
        httpExchange.getResponseHeaders().add("Content-Type", "application/json");

        // get requestBody and resolve to WebHookConfig
        String requestBody = NetUtils.parsePostBody(httpExchange);
        JsonNode node = JsonUtils.getJsonNode(requestBody);
        WebHookConfig webHookConfig = JsonUtils.toObject(node.get("webHookConfig").toString(), WebHookConfig.class);
        Integer pageNum = Integer.parseInt(node.get("pageNum").toString());
        Integer pageSize = Integer.parseInt(node.get("pageSize").toString());

        AdminWebHookConfigOperationManage manage = new AdminWebHookConfigOperationManage();
        try (OutputStream out = httpExchange.getResponseBody()) {
            WebHookConfigOperation operation = manage.getHookConfigOperationManage();
            List<WebHookConfig> result = operation.queryWebHookConfigByManufacturer(webHookConfig, pageNum, pageSize); // operating result
            out.write(JsonUtils.toJson(result).getBytes());
        } catch (Exception e) {
            logger.error("get WebHookConfigOperation implementation Failed.", e);
        }
    }
}
