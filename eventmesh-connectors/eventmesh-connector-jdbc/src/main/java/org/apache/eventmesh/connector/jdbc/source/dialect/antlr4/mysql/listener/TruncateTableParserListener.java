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

package org.apache.eventmesh.connector.jdbc.source.dialect.antlr4.mysql.listener;

import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.TruncateTableContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParserBaseListener;
import org.apache.eventmesh.connector.jdbc.source.dialect.antlr4.mysql.MysqlAntlr4DdlParser;
import org.apache.eventmesh.connector.jdbc.utils.Antlr4Utils;
import org.apache.eventmesh.connector.jdbc.utils.JdbcStringUtils;

/**
 * A custom ANTLR listener for parsing TRUNCATE TABLE statements.
 * <a href="https://dev.mysql.com/doc/refman/8.0/en/truncate-table.html">TRUNCATE TABLE Statement</a>
 * <pre>
 *     TRUNCATE [TABLE] tbl_name
 * </pre>
 */
public class TruncateTableParserListener extends MySqlParserBaseListener {

    private MysqlAntlr4DdlParser parser;

    /**
     * Constructs a TruncateTableParserListener with the specified parser.
     *
     * @param parser The MysqlAntlr4DdlParser used for parsing.
     */
    public TruncateTableParserListener(MysqlAntlr4DdlParser parser) {
        this.parser = parser;
    }

    @Override
    public void enterTruncateTable(TruncateTableContext ctx) {
        String sql = Antlr4Utils.getText(ctx);
        String tableName = JdbcStringUtils.withoutWrapper(ctx.tableName().fullId().getText());
        //TruncateTableEvent event = new TruncateTableEvent(parser.getCurrentDatabase(), tableName, sql);
        parser.handleEvent(null);
        super.enterTruncateTable(ctx);
    }
}

