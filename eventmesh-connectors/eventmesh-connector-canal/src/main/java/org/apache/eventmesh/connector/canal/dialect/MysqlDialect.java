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

package org.apache.eventmesh.connector.canal.dialect;

import org.apache.eventmesh.connector.canal.template.MysqlSqlTemplate;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.lob.LobHandler;


public class MysqlDialect extends AbstractDbDialect {

    public MysqlDialect(JdbcTemplate jdbcTemplate, LobHandler lobHandler) {
        super(jdbcTemplate, lobHandler);
        sqlTemplate = new MysqlSqlTemplate();
    }

    public boolean isCharSpacePadded() {
        return false;
    }

    public boolean isCharSpaceTrimmed() {
        return true;
    }

    public boolean isEmptyStringNulled() {
        return false;
    }

    public boolean isSupportMergeSql() {
        return true;
    }

    public String getDefaultSchema() {
        return null;
    }

    public String getDefaultCatalog() {
        return jdbcTemplate.queryForObject("select database()", String.class);
    }

}
