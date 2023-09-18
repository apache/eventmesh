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
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.DropDatabaseContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParserBaseListener;
import org.apache.eventmesh.connector.jdbc.event.DropDatabaseEvent;
import org.apache.eventmesh.connector.jdbc.event.SchemaChangeEventType;
import org.apache.eventmesh.connector.jdbc.source.config.SourceConnectorConfig;
import org.apache.eventmesh.connector.jdbc.source.dialect.antlr4.mysql.MysqlAntlr4DdlParser;
import org.apache.eventmesh.connector.jdbc.source.dialect.mysql.MysqlSourceMateData;
import org.apache.eventmesh.connector.jdbc.table.catalog.CatalogSchema;
import org.apache.eventmesh.connector.jdbc.table.catalog.TableId;
import org.apache.eventmesh.connector.jdbc.utils.Antlr4Utils;
import org.apache.eventmesh.connector.jdbc.utils.JdbcStringUtils;

public class DropDatabaseParserListener extends MySqlParserBaseListener {

    private MysqlAntlr4DdlParser parser;

    public DropDatabaseParserListener(MysqlAntlr4DdlParser parser) {
        this.parser = parser;
    }

    @Override
    public void enterDropDatabase(DropDatabaseContext ctx) {

        String databaseName = JdbcStringUtils.withoutWrapper(ctx.uid().getText());
        parser.getCatalogTableSet().removeDatabase(databaseName);
        if (parser.getCallback() != null) {
            String sql = Antlr4Utils.getText(ctx);
            CatalogSchema catalogSchema = new CatalogSchema(databaseName);
            DropDatabaseEvent event = new DropDatabaseEvent(new TableId(databaseName));
            Payload payload = event.getJdbcConnectData().getPayload();
            SourceConnectorConfig sourceConnectorConfig = parser.getSourceConfig().getSourceConnectorConfig();
            MysqlSourceMateData sourceMateData = MysqlSourceMateData.newBuilder()
                    .name(sourceConnectorConfig.getName())
                    .catalogName(databaseName)
                    .serverId(sourceConnectorConfig.getMysqlConfig().getServerId())
                    .build();
            CatalogChanges changes = CatalogChanges.newBuilder().operationType(SchemaChangeEventType.DATABASE_DROP).catalog(catalogSchema).build();
            payload.withSource(sourceMateData).withDdl(sql).withCatalogChanges(changes);
            parser.getCallback().handle(event);
        }
        super.enterDropDatabase(ctx);
    }
}
