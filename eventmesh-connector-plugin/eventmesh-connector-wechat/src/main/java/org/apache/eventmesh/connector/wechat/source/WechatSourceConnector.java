/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.connector.wechat.source;

import org.apache.eventmesh.connector.SourceConnector;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import lombok.extern.slf4j.Slf4j;

/**
 * New-architecture WeChat Official Account source connector: handles the server-to-server
 * message callback (GET signature verification + POST XML message push) and exposes messages
 * as CloudEvents.
 */
@Slf4j
public class WechatSourceConnector implements SourceConnector {

    private int port;
    private String path;
    private String token;

    private HttpServer server;
    private final LinkedBlockingQueue<CloudEvent> buffer = new LinkedBlockingQueue<>();

    @Override
    public void init(Properties props) {
        this.port = Integer.parseInt(props.getProperty("connector.port", "8094"));
        this.path = props.getProperty("connector.path", "/wechat");
        this.token = props.getProperty("connector.token", "eventmesh");
    }

    private synchronized void ensureStarted() {
        if (server != null) {
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext(path, this::handle);
            server.start();
            log.info("wechat source listening on {}{}", port, path);
        } catch (Exception e) {
            throw new RuntimeException("wechat source server start failed: " + e.getMessage(), e);
        }
    }

    private void handle(HttpExchange exchange) {
        try {
            String query = exchange.getRequestURI().getQuery();
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleVerification(exchange, query);
                return;
            }
            byte[] body = exchange.getRequestBody().readAllBytes();
            Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(new ByteArrayInputStream(body));
            Element root = doc.getDocumentElement();
            String fromUser = textOf(root, "FromUserName");
            String msgType = textOf(root, "MsgType");
            CloudEvent event = CloudEventBuilder.v1()
                .withId("wechat-" + textOf(root, "MsgId"))
                .withSource(URI.create("wechat"))
                .withType("wechat." + msgType)
                .withSubject(fromUser)
                .withDataContentType("application/xml")
                .withData(body)
                .build();
            buffer.offer(event);
            byte[] resp = "success".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        } catch (Exception e) {
            log.warn("wechat source request failed: {}", e.toString());
            try {
                exchange.sendResponseHeaders(500, -1);
            } catch (Exception ignored) {
                // client gone
            }
        }
    }

    private void handleVerification(HttpExchange exchange, String query) throws Exception {
        // WeChat server check: echostr when sha1(sort(token,timestamp,nonce)) == signature
        String signature = param(query, "signature");
        String timestamp = param(query, "timestamp");
        String nonce = param(query, "nonce");
        String echostr = param(query, "echostr");
        String[] parts = {token, timestamp, nonce};
        Arrays.sort(parts);
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] digest = sha1.digest(String.join("", parts).getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        byte[] resp = hex.toString().equals(signature)
            ? echostr.getBytes(StandardCharsets.UTF_8) : "verification failed".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, resp.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(resp);
        }
    }

    private static String textOf(Element root, String tag) {
        try {
            return root.getElementsByTagName(tag).item(0).getTextContent();
        } catch (Exception e) {
            return "";
        }
    }

    private static String param(String query, String key) {
        if (query == null) {
            return "";
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && key.equals(pair.substring(0, eq))) {
                return pair.substring(eq + 1);
            }
        }
        return "";
    }

    @Override
    public List<CloudEvent> poll() {
        ensureStarted();
        List<CloudEvent> out = new ArrayList<>(buffer.size());
        buffer.drainTo(out);
        return out;
    }

    @Override
    public void commit(CloudEvent lastPublished) {
        // Push-style source: we reply "success" synchronously, checkpoint-free.
    }
}
