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

package org.apache.eventmesh.runtime.http;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import lombok.extern.slf4j.Slf4j;

/**
 * Builds an {@link SSLContext} for the HTTPS servers (§4.5) from a PKCS12/JKS keystore.
 *
 * <pre>
 *   SSLContext ctx = TlsContextFactory.fromKeystore("/path/server.p12", "changeit".toCharArray(), null);
 *   new UniHttpServer(ingress, admin).withTls(ctx).start(8443);
 * </pre>
 *
 * <p>mTLS: pass a truststore + set {@code needClientAuth} on the {@code HttpsConfigurator}'s
 * {@code SSLEngine} (left to the caller; the SSLContext here loads both key + trust material).</p>
 */
@Slf4j
public final class TlsContextFactory {

    private TlsContextFactory() {
    }

    /**
     * Load keystore + optional truststore into a TLSv1.3 SSLContext. Truststore password defaults to
     * the keystore password (convenience for the common single-secret case).
     */
    public static SSLContext fromKeystore(String keystorePath, char[] keystorePass, String truststorePath) throws Exception {
        return fromKeystore(keystorePath, keystorePass, truststorePath, keystorePass, "TLSv1.3");
    }

    /**
     * @param keystorePath    server identity (PKCS12/JKS)
     * @param keystorePass    keystore password
     * @param truststorePath  optional client-trust material for mTLS (null = no client-auth verification)
     * @param truststorePass  truststore password (independent of keystore; was previously hardcoded to keystorePass — bug G14)
     * @param protocol        TLS protocol (§13.4.1 / A.5 default TLSv1.3; was hardcoded TLSv1.2 — G14)
     */
    public static SSLContext fromKeystore(String keystorePath, char[] keystorePass,
        String truststorePath, char[] truststorePass, String protocol) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream is = new FileInputStream(keystorePath)) {
            keyStore.load(is, keystorePass);
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keystorePass);

        SSLContext sslContext = SSLContext.getInstance(protocol);
        if (truststorePath != null) {
            // mTLS: load client-trust material so the server verifies client certs.
            KeyStore trustStore = KeyStore.getInstance("PKCS12");
            try (InputStream is = new FileInputStream(truststorePath)) {
                trustStore.load(is, truststorePass);
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
            log.info("TLS context loaded (protocol={}, mTLS truststore={})", protocol, truststorePath);
        } else {
            sslContext.init(kmf.getKeyManagers(), null, new SecureRandom());
            log.info("TLS context loaded (protocol={}, no client-auth)", protocol);
        }
        return sslContext;
    }
}
