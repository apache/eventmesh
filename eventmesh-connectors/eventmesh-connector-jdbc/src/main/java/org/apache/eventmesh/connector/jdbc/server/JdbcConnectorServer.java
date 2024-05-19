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

package org.apache.eventmesh.connector.jdbc.server;

import org.apache.eventmesh.connector.jdbc.config.JdbcServerConfig;
import org.apache.eventmesh.connector.jdbc.sink.JdbcSinkConnector;
import org.apache.eventmesh.connector.jdbc.source.JdbcSourceConnector;
import org.apache.eventmesh.openconnect.Application;
import org.apache.eventmesh.openconnect.util.ConfigUtil;

/**
 * JDBC connector server
 */
public class JdbcConnectorServer {

    public static void main(String[] args) throws Exception {
        JdbcServerConfig serverConfig = ConfigUtil.parse(JdbcServerConfig.class, "server-config.yml");

        if (serverConfig.isSourceEnable()) {
            Application jdbcSourceApp = new Application();
            jdbcSourceApp.run(JdbcSourceConnector.class);
        }

        if (serverConfig.isSinkEnable()) {
            Application jdbcSinkApp = new Application();
            jdbcSinkApp.run(JdbcSinkConnector.class);
        }
    }

}
