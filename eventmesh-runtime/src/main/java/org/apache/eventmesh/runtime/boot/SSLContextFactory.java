/*
 * Licensed to Apache Software Foundation (ASF) under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Apache Software Foundation (ASF) licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.eventmesh.runtime.boot;

import org.apache.eventmesh.runtime.constants.EventMeshConstants;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SSLContextFactory {
    private static Logger httpLogger = LoggerFactory.getLogger("http");

    private static String protocol = "TLSv1.1";

    private static String fileName;

    private static String pass;


    public static SSLContext getSslContext() {
        SSLContext sslContext;
        try {
            protocol = System.getProperty("ssl.server.protocol", "TLSv1.1");

            fileName = System.getProperty("ssl.server.cer", "sChat2.jks");

            char[] filePass = null;
            pass = System.getProperty("ssl.server.pass", "sNetty");
            if (StringUtils.isNotBlank(pass)) {
                filePass = pass.toCharArray();
            }
            sslContext = SSLContext.getInstance(protocol);
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(Files.newInputStream(Paths.get(EventMeshConstants.EVENTMESH_CONF_HOME
                    + File.separator
                    + fileName), StandardOpenOption.READ), filePass);
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, filePass);
            sslContext.init(kmf.getKeyManagers(), null, null);
        } catch (Exception e) {
            httpLogger.warn("sslContext init failed", e);
            sslContext = null;
        }
        return sslContext;
    }
}
