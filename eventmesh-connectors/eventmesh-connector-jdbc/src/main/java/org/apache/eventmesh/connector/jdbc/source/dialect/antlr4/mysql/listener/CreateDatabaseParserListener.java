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

import org.apache.eventmesh.connector.jdbc.CatalogChanges;
import org.apache.eventmesh.connector.jdbc.Payload;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.CreateDatabaseContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.CreateDatabaseOptionContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParserBaseListener;
import org.apache.eventmesh.connector.jdbc.event.CreateDatabaseEvent;
import org.apache.eventmesh.connector.jdbc.event.SchemaChangeEventType;
import org.apache.eventmesh.connector.jdbc.source.config.SourceConnectorConfig;
import org.apache.eventmesh.connector.jdbc.source.dialect.antlr4.mysql.MysqlAntlr4DdlParser;
import org.apache.eventmesh.connector.jdbc.source.dialect.mysql.MysqlSourceMateData;
import org.apache.eventmesh.connector.jdbc.table.catalog.CatalogSchema;
import org.apache.eventmesh.connector.jdbc.table.catalog.TableId;
import org.apache.eventmesh.connector.jdbc.utils.Antlr4Utils;
import org.apache.eventmesh.connector.jdbc.utils.JdbcStringUtils;

/**
 * Listener for parsing create database statements using ANTLR4.
 * <p>
 * Mysql <a href="https://dev.mysql.com/doc/refman/8.0/en/create-database.html">CREATE DATABASE Statement:</a>
 * <blockquote><pre>
 *  CREATE {DATABASE | SCHEMA} [IF NOT EXISTS] db_name
 *     [create_option] ...
 *  create_option: [DEFAULT] {
 *     CHARACTER SET [=] charset_name
 *   | COLLATE [=] collation_name
 *   | ENCRYPTION [=] {'Y' | 'N'}
 * }
 * </pre></blockquote>
 */
public class CreateDatabaseParserListener extends MySqlParserBaseListener {

    private String databaseName;

    private String charSetName;

    private String collate;

    private String encryption;

    private MysqlAntlr4DdlParser parser;

    public CreateDatabaseParserListener(MysqlAntlr4DdlParser parser) {
        this.parser = parser;
    }

    @Override
    public void enterCreateDatabase(CreateDatabaseContext ctx) {
        this.databaseName = JdbcStringUtils.withoutWrapper(ctx.uid().getText());
        super.enterCreateDatabase(ctx);
    }

    @Override
    public void exitCreateDatabase(CreateDatabaseContext ctx) {
        if (parser.getCallback() != null) {
            String sql = Antlr4Utils.getText(ctx);
            CatalogSchema catalogSchema = new CatalogSchema(databaseName, charSetName);
            CreateDatabaseEvent event = new CreateDatabaseEvent(new TableId(databaseName));
            Payload payload = event.getJdbcConnectData().getPayload();
            SourceConnectorConfig sourceConnectorConfig = parser.getSourceConfig().getSourceConnectorConfig();
            MysqlSourceMateData sourceMateData = MysqlSourceMateData.newBuilder()
                    .name(sourceConnectorConfig.getName())
                    .catalogName(databaseName)
                    .serverId(sourceConnectorConfig.getMysqlConfig().getServerId())
                    .build();
            CatalogChanges changes = CatalogChanges.newBuilder().operationType(SchemaChangeEventType.DATABASE_CREATE).catalog(catalogSchema).build();
            payload.withSource(sourceMateData).withDdl(sql).withCatalogChanges(changes);
            parser.getCallback().handle(event);
        }
        super.exitCreateDatabase(ctx);
    }

    @Override
    public void enterCreateDatabaseOption(CreateDatabaseOptionContext ctx) {
        this.charSetName = ctx.charsetName().getText();
        this.collate = ctx.COLLATE().getText();
        this.encryption = ctx.ENCRYPTION().getText();
        super.enterCreateDatabaseOption(ctx);
    }
}
