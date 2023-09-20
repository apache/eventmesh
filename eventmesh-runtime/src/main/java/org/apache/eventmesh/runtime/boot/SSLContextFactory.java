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

package org.apache.eventmesh.runtime.boot;

import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

public class SSLContextFactory {

    private static String protocol = "TLSv1.1";

    private static String fileName;

    private static String password;

    public static SSLContext getSslContext(final EventMeshHTTPConfiguration eventMeshHttpConfiguration) throws Exception {
        SSLContext sslContext;

        try (
                InputStream inputStream = Files.newInputStream(Paths.get(EventMeshConstants.EVENTMESH_CONF_HOME
                        + File.separator
                        + fileName), StandardOpenOption.READ)) {
            protocol = eventMeshHttpConfiguration.getEventMeshServerSSLProtocol();
            fileName = eventMeshHttpConfiguration.getEventMeshServerSSLCer();
            password = eventMeshHttpConfiguration.getEventMeshServerSSLPass();

            char[] filePass = StringUtils.isNotBlank(password) ? password.toCharArray() : new char[0];
            final KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(inputStream, filePass);
            final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, filePass);

            sslContext = SSLContext.getInstance(protocol);
            sslContext.init(kmf.getKeyManagers(), null, null);
        }

        return sslContext;
    }
}
