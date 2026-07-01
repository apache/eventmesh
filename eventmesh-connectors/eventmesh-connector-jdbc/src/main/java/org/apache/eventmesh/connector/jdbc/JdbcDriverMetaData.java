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

package org.apache.eventmesh.connector.jdbc;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

/**
 * Represents metadata information about a JDBC driver
 */
@Data
@AllArgsConstructor
@ToString
public class JdbcDriverMetaData {

    // The major version number of the JDBC driver
    private final int jdbcMajorVersion;

    // The minor version number of the JDBC driver
    private final int jdbcMinorVersion;

    // The name of the JDBC driver
    private final String jdbcDriverName;

    // The name of the database product
    private final String databaseProductName;

    // The version of the database product
    private final String databaseProductVersion;

}
