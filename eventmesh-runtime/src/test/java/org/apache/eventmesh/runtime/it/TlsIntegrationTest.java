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

package org.apache.eventmesh.runtime.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.runtime.admin.UniAdminService;
import org.apache.eventmesh.runtime.http.TlsContextFactory;
import org.apache.eventmesh.runtime.http.UniHttpServer;
import org.apache.eventmesh.runtime.ingress.UniIngressService;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;

/**
 * End-to-end TLS integration test (§13.4.1): the traffic {@link UniHttpServer} is booted with
 * {@code withTls(sslContext)} and serves over HTTPS. A client trusting the self-signed cert posts a
 * CloudEvent to {@code /events/publish} and gets 202. In-memory storage — no broker.
 *
 * <p>The self-signed PKCS12 keystore is generated via the JDK {@code keytool} (no BouncyCastle, no
 * {@code sun.security.x509} module-exports) into a temp file, then loaded by
 * {@link TlsContextFactory#fromKeystore}. The client installs a permissive {@link TrustManager}.</p>
 */
class TlsIntegrationTest {

    private static final String STOREPASS = "changeit";

    private UniHttpServer server;
    private int port;
    private InMemoryStorage storage;
    private Path keystore;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
        if (keystore != null) {
            try {
                Files.deleteIfExists(keystore);
            } catch (Exception ignored) {
                // best-effort
            }
        }
    }

    @Test
    void httpsPublishOverTls() throws Exception {
        boot();

        byte[] body = EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE)
            .serialize(CloudEventBuilder.v1().withId("e1").withSource(URI.create("it")).withType("it.event").build());

        // HTTPS publish → 202.
        HttpURLConnection conn = (HttpURLConnection) new URL("https://localhost:" + port + "/events/publish?topic=tls").openConnection();
        assertTrue(conn instanceof HttpsURLConnection, "connection should be HTTPS");
        ((HttpsURLConnection) conn).setSSLSocketFactory(trustAllContext().getSocketFactory());
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/cloudevents+json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body);
        }
        assertEquals(202, conn.getResponseCode(), "publish over HTTPS should be accepted");
        conn.getInputStream().close();

        // The event reached storage.
        List<CloudEvent> got = storage.poll("tls", -1, -1, 100, 0);
        assertEquals(1, got.size(), "the TLS-published event should reach storage");
    }

    private void boot() throws Exception {
        // Generate a self-signed PKCS12 keystore via keytool (avoids sun.security.x509 module exports).
        keystore = Files.createTempFile("em-tls-it-", ".p12");
        Files.delete(keystore); // keytool refuses to overwrite; let it create the file.
        new ProcessBuilder("keytool",
            "-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize", "2048",
            "-sigalg", "SHA256withRSA", "-dname", "CN=localhost", "-validity", "1",
            "-keystore", keystore.toString(), "-storetype", "PKCS12",
            "-storepass", STOREPASS, "-keypass", STOREPASS)
            .redirectErrorStream(true).start().waitFor();

        SSLContext ctx = TlsContextFactory.fromKeystore(keystore.toString(), STOREPASS.toCharArray(), null);

        storage = new InMemoryStorage();
        UniIngressService ingress = new UniIngressService(storage, new InMemoryOffsetStore());
        UniAdminService admin = new UniAdminService(ingress);
        server = new UniHttpServer(ingress, admin).withTls(ctx);
        port = server.start(0);
    }

    /** A client SSLContext that trusts the self-signed cert (permissive TrustManager). */
    private static SSLContext trustAllContext() throws Exception {
        SSLContext ctx = SSLContext.getInstance("TLSv1.3");
        TrustManager[] tms = new TrustManager[]{
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                    // no-op
                }

                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                    // no-op - trust the self-signed cert
                }

                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[0];
                }
            }
        };
        ctx.init(null, tms, null);
        return ctx;
    }

    /** In-memory storage stub the IT reads to confirm the published event landed. */
    static final class InMemoryStorage implements MeshStoragePlugin {

        private final ConcurrentHashMap<String, Queue<CloudEvent>> queues = new ConcurrentHashMap<>();

        @Override
        public void init(java.util.Properties p) {
            // no-op
        }

        @Override
        public void send(String topic, CloudEvent event, SendCallback cb) {
            queues.computeIfAbsent(topic, k -> new ConcurrentLinkedQueue<>()).offer(event);
            SendResult r = new SendResult();
            r.setMessageId(event.getId());
            r.setTopic(topic);
            cb.onSuccess(r);
        }

        @Override
        public List<CloudEvent> poll(String topic, int partition, long startOffset, int maxEvents, long timeoutMs) {
            Queue<CloudEvent> q = queues.get(topic);
            if (q == null) {
                return new ArrayList<>();
            }
            List<CloudEvent> out = new ArrayList<>();
            CloudEvent e;
            while (out.size() < maxEvents && (e = q.poll()) != null) {
                out.add(e);
            }
            return out;
        }

        @Override
        public void assignPartitions(String topic, List<Integer> partitions) {
            // no-op
        }

        @Override
        public void commitOffset(String topic, int partition, long offset) {
            // no-op
        }

        @Override
        public boolean isStarted() {
            return true;
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public void start() {
            // no-op
        }

        @Override
        public void shutdown() {
            // no-op
        }
    }
}




