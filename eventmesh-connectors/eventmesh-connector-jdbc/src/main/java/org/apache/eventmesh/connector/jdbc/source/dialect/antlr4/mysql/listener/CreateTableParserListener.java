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
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.ColumnCreateTableContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.CopyCreateTableContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.DecimalLiteralContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.QueryCreateTableContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.TableOptionAutoIncrementContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.TableOptionCharsetContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.TableOptionCollateContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.TableOptionEngineContext;
import org.apache.eventmesh.connector.jdbc.event.CreateTableEvent;
import org.apache.eventmesh.connector.jdbc.event.SchemaChangeEventType;
import org.apache.eventmesh.connector.jdbc.source.config.SourceConnectorConfig;
import org.apache.eventmesh.connector.jdbc.source.dialect.antlr4.mysql.MysqlAntlr4DdlParser;
import org.apache.eventmesh.connector.jdbc.source.dialect.mysql.MysqlSourceMateData;
import org.apache.eventmesh.connector.jdbc.table.catalog.Table;
import org.apache.eventmesh.connector.jdbc.table.catalog.TableId;
import org.apache.eventmesh.connector.jdbc.table.catalog.mysql.MysqlOptions.MysqlTableOptions;
import org.apache.eventmesh.connector.jdbc.table.catalog.mysql.MysqlTableEditor;
import org.apache.eventmesh.connector.jdbc.table.catalog.mysql.MysqlTableSchema;
import org.apache.eventmesh.connector.jdbc.utils.Antlr4Utils;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * <pre>
 * listen for events while parsing a CREATE TABLE statement in a MySQL DDL (Data Definition Language) script.
 * </pre>
 *
 * <a href="https://dev.mysql.com/doc/refman/8.0/en/create-table.html">MYSQL CREATE TABLE</a>
 *
 * <pre>
 * CREATE [TEMPORARY] TABLE [IF NOT EXISTS] tbl_name
 *     (create_definition,...)
 *     [table_options]
 *     [partition_options]
 * </pre>
 *
 * <pre>
 * CREATE [TEMPORARY] TABLE [IF NOT EXISTS] tbl_name
 *     [(create_definition,...)]
 *     [table_options]
 *     [partition_options]
 *     [IGNORE | REPLACE]
 *     [AS] query_expression
 * </pre>
 * <pre>
 * CREATE [TEMPORARY] TABLE [IF NOT EXISTS] tbl_name
 *     { LIKE old_tbl_name | (LIKE old_tbl_name) }
 * </pre>
 */
public class CreateTableParserListener extends TableBaseParserListener {

    public CreateTableParserListener(List<ParseTreeListener> listeners, MysqlAntlr4DdlParser parser) {
        super(listeners, parser);
    }

    @Override
    public void enterCopyCreateTable(CopyCreateTableContext ctx) {
        // TODO support next version
        super.enterCopyCreateTable(ctx);
    }

    @Override
    public void enterQueryCreateTable(QueryCreateTableContext ctx) {
        // TODO support next version
        super.enterQueryCreateTable(ctx);
    }

    @Override
    public void enterColumnCreateTable(ColumnCreateTableContext ctx) {
        String tableName = ctx.tableName().fullId().getText();
        this.tableEditor = createTableEditor(tableName);
        super.enterColumnCreateTable(ctx);
    }

    @Override
    public void exitColumnCreateTable(ColumnCreateTableContext ctx) {
        String ddl = Antlr4Utils.getText(ctx);
        parser.runIfAllNotNull(() -> {
            listeners.remove(columnDefinitionListener);
            // help JVM GC
            columnDefinitionListener = null;
            MysqlTableSchema tableSchema = tableEditor.build();
            parser.getCatalogTableSet().overrideTable(tableSchema);
            String currentDatabase = parser.getCurrentDatabase();
            CreateTableEvent event = new CreateTableEvent(new TableId(currentDatabase, null, tableSchema.getSimpleName()));
            Payload payload = event.getJdbcConnectData().getPayload();
            SourceConnectorConfig sourceConnectorConfig = parser.getSourceConfig().getSourceConnectorConfig();
            MysqlSourceMateData sourceMateData = MysqlSourceMateData.newBuilder()
                .name(sourceConnectorConfig.getName())
                .catalogName(currentDatabase)
                .serverId(sourceConnectorConfig.getMysqlConfig().getServerId())
                .build();
            Table table = Table.newBuilder().withTableId(tableSchema.getTableId())
                .withPrimaryKey(tableSchema.getPrimaryKey())
                .withUniqueKeys(tableSchema.getUniqueKeys())
                .withComment(tableSchema.getComment())
                .withOptions(tableSchema.getTableOptions())
                .build();
            CatalogChanges changes = CatalogChanges.newBuilder().operationType(SchemaChangeEventType.TABLE_CREATE).table(table)
                .columns(tableSchema.getColumns()).build();
            payload.withSource(sourceMateData).withDdl(ddl).withCatalogChanges(changes);
            parser.handleEvent(event);
        }, tableEditor);
        // reset column order
        columnOrder.set(1);
        super.exitColumnCreateTable(ctx);
    }

    private MysqlTableEditor createTableEditor(String tableName) {
        TableId tableId = parser.parseTableId(tableName);
        if (StringUtils.isBlank(tableId.getCatalogName())) {
            tableId.setCatalogName(parser.getCurrentDatabase());
        }
        return MysqlTableEditor.ofCatalogTableEditor(tableId);
    }

    @Override
    public void enterTableOptionEngine(TableOptionEngineContext ctx) {
        if (ctx.ENGINE() != null) {
            this.tableEditor.withOption(MysqlTableOptions.ENGINE, ctx.engineName().getText());
        }
        super.enterTableOptionEngine(ctx);
    }

    @Override
    public void enterTableOptionCharset(TableOptionCharsetContext ctx) {

        List<TerminalNode> nodes = ctx.DEFAULT();
        if (CollectionUtils.isNotEmpty(nodes) && nodes.size() == 2) {
            TerminalNode node = nodes.get(1);
            this.tableEditor.withOption(MysqlTableOptions.CHARSET, node.getText());
        } else {
            this.tableEditor.withOption(MysqlTableOptions.CHARSET, ctx.charsetName().getText());
        }

        super.enterTableOptionCharset(ctx);
    }

    @Override
    public void enterTableOptionAutoIncrement(TableOptionAutoIncrementContext ctx) {
        DecimalLiteralContext decimalLiteralContext = ctx.decimalLiteral();
        if (decimalLiteralContext != null) {
            String autoIncrementNumber = Antlr4Utils.getText(decimalLiteralContext);
            this.tableEditor.withOption(MysqlTableOptions.AUTO_INCREMENT, autoIncrementNumber);
        }
        super.enterTableOptionAutoIncrement(ctx);
    }

    @Override
    public void enterTableOptionCollate(TableOptionCollateContext ctx) {
        if (ctx.COLLATE() != null) {
            this.tableEditor.withOption(MysqlTableOptions.COLLATE, ctx.collationName().getText());
        }
        super.enterTableOptionCollate(ctx);
    }
}
