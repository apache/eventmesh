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

package org.apache.eventmesh.client.http.ssl;

import org.apache.commons.lang3.StringUtils;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class MyX509TrustManager implements X509TrustManager {
    X509TrustManager myTrustManager;

    public MyX509TrustManager() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        String fileName = System.getProperty("ssl.client.cer", "");
        String pass = System.getProperty("ssl.client.pass", "");
        char[] filePass = null;
        if (StringUtils.isNotBlank(pass)) {
            filePass = pass.toCharArray();
        }
        keyStore.load(Files.newInputStream(Paths.get(System.getProperty("confPath", System.getenv("confPath"))
                + File.separator
                + fileName), StandardOpenOption.READ), filePass);
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);
        TrustManager trustManagers[] = trustManagerFactory.getTrustManagers();
        for (TrustManager trustManager : trustManagers) {
            if (trustManager instanceof X509TrustManager) {
                myTrustManager = (X509TrustManager) trustManager;
                return;
            }
        }
        throw new Exception("Couldn't initialize");
    }

    @Override
    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
    }

    @Override
    public void checkServerTrusted(X509Certificate[] certificates, String authType) throws CertificateException {
        if ((certificates != null) && (certificates.length == 1)) {
            certificates[0].checkValidity();
        } else {
            myTrustManager.checkServerTrusted(certificates, authType);
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return myTrustManager.getAcceptedIssuers();
    }
}
