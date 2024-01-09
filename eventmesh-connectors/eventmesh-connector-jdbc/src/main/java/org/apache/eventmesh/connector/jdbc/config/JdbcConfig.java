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

package org.apache.eventmesh.connector.jdbc.config;

import java.util.Properties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the configuration for a JDBC connection.
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
public class JdbcConfig {

    private String databaseName;

    // The hostname of the database server.
    private String hostname;

    // The port number of the database server.
    private int port;

    // The username for the database connection.
    private String user;

    // The password for the database connection.
    private String password;

    private String initialStatements;

    private int connectTimeout;

    /**
     * Converts the JdbcConfig object to a Properties object containing the user and password.
     *
     * @return The Properties object representing the JdbcConfig.
     */
    public Properties asProperties() {
        Properties props = new Properties();
        props.put("user", this.user);
        props.put("password", this.password);
        return props;
    }
}
