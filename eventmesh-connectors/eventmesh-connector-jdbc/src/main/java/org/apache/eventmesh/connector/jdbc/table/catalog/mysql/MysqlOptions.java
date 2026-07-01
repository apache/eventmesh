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

package org.apache.eventmesh.connector.jdbc.table.catalog.mysql;

/**
 * The MysqlOptions class provides the constants for configuring options in MySQL tables and columns.
 */
public class MysqlOptions {

    public static final class MysqlTableOptions {

        public static String ENGINE = "ENGINE";

        public static String AUTO_INCREMENT = "AUTO_INCREMENT";

        public static String CHARSET = "CHARSET";

        public static String COLLATE = "COLLATE";

        public static String COMMENT = "COMMENT";
    }

    public static final class MysqlColumnOptions {

        public static String SIGNED = "SIGNED";

        public static String UNSIGNED = "UNSIGNED";

        public static String ZEROFILL = "ZEROFILL";
    }

}
