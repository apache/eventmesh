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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;

import com.sun.net.httpserver.HttpServer;

/**
 * Hermetic unit test: WechatSourceConnector lazily boots its webhook endpoint on first poll; a native wechat
 * callback payload POSTed to the hook surfaces via poll() as a CloudEvent carrying the platform
 * message id / subject mapping.
 */
class WechatSourceConnectorTest {

    private static final String BODY = "<xml><ToUserName><![CDATA[gh_1]]></ToUserName>"
        + "<FromUserName><![CDATA[o-1]]></FromUserName>"
        + "<MsgType><![CDATA[text]]></MsgType>"
        + "<Content><![CDATA[hello-wechat]]></Content>"
        + "<MsgId>123</MsgId></xml>";

    private int hookPort(WechatSourceConnector source) throws Exception {
        Field f = WechatSourceConnector.class.getDeclaredField("server");
        f.setAccessible(true);
        HttpServer s = (HttpServer) f.get(source);
        return s.getAddress().getPort();
    }

    private WechatSourceConnector boot() {
        WechatSourceConnector source = new WechatSourceConnector();
        Properties props = new Properties();
        props.setProperty("connector.port", "0");
        props.setProperty("connector.path", "/wechat");
        // no secret/token set -> signature checks are disabled (dev mode)
        source.init(props);
        return source;
    }

    @Test
    void postedCallbackSurfacesAsEvent() throws Exception {
        WechatSourceConnector source = boot();
        source.poll(); // trigger lazy server start
        int port = hookPort(source);

        HttpURLConnection conn = (HttpURLConnection) new URL(
            "http://127.0.0.1:" + port + "/wechat").openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type",
            BODY.startsWith("<") ? "text/xml" : "application/json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(BODY.getBytes(StandardCharsets.UTF_8));
        }
        assertTrue(conn.getResponseCode() < 400, "callback must be accepted, got "
            + conn.getResponseCode());
        conn.disconnect();

        List<CloudEvent> events = source.poll();
        assertEquals(1, events.size(), "callback must surface as exactly one event");
        CloudEvent event = events.get(0);
        assertEquals("wechat.text", event.getType());
        assertEquals("o-1", event.getSubject());
        assertTrue(event.getId().startsWith("wechat-"),
            "event id must be namespaced by the platform, got: " + event.getId());
    }
}
