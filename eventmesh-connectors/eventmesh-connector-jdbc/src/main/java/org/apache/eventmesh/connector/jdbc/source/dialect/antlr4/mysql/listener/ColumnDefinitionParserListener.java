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

import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.AutoIncrementColumnConstraintContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.CollateColumnConstraintContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.ColumnDefinitionContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.CommentColumnConstraintContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.NullNotnullContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.PrimaryKeyColumnConstraintContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.UniqueKeyColumnConstraintContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParserBaseListener;
import org.apache.eventmesh.connector.jdbc.source.dialect.antlr4.mysql.MysqlAntlr4DdlParser;
import org.apache.eventmesh.connector.jdbc.source.dialect.mysql.MysqlDataTypeConvertor;
import org.apache.eventmesh.connector.jdbc.table.catalog.TableEditor;
import org.apache.eventmesh.connector.jdbc.table.catalog.mysql.MysqlColumnEditor;
import org.apache.eventmesh.connector.jdbc.table.type.EventMeshDataType;
import org.apache.eventmesh.connector.jdbc.utils.JdbcStringUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.antlr.v4.runtime.tree.ParseTreeListener;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ColumnDefinitionParserListener extends MySqlParserBaseListener {

    private DefaultValueParserListener defaultValueParserListener;

    private final List<ParseTreeListener> listeners;

    private TableEditor tableEditor;

    private MysqlColumnEditor columnEditor;

    private final MysqlAntlr4DdlParser parser;

    private MysqlDataTypeConvertor dataTypeConvertor;

    // Determines whether the current column definition should be ignored. e.g. PRIMARY KEY, UNIQUE KEY
    private AtomicReference<Boolean> ignoreColumn = new AtomicReference<>(false);

    public ColumnDefinitionParserListener(List<ParseTreeListener> listeners, TableEditor tableEditor, MysqlColumnEditor columnEditor,
                                          MysqlAntlr4DdlParser parser) {
        this.listeners = listeners;
        this.tableEditor = tableEditor;
        this.columnEditor = columnEditor;
        this.parser = parser;
        this.dataTypeConvertor = new MysqlDataTypeConvertor();
    }

    @Override
    public void enterColumnDefinition(ColumnDefinitionContext ctx) {
        // parse Column data type
        this.parser.runIfAllNotNull(() -> {
            String dataTypeString = ctx.dataType().getText();
            EventMeshDataType<?> eventMeshType = this.dataTypeConvertor.toEventMeshType(dataTypeString);
            this.columnEditor.withEventMeshType(eventMeshType);
            this.columnEditor.withJdbcType(this.dataTypeConvertor.toJDBCType(dataTypeString));
            this.columnEditor.withType(dataTypeString);
        }, columnEditor);

        this.parser.runIfAllNotNull(() -> {
            // parse column default value
            ColumnDefinitionParserListener.this.defaultValueParserListener = new DefaultValueParserListener(columnEditor);
            ColumnDefinitionParserListener.this.listeners.add(defaultValueParserListener);
        }, tableEditor, columnEditor);

        super.enterColumnDefinition(ctx);
    }

    @Override
    public void enterNullNotnull(NullNotnullContext ctx) {
        columnEditor.notNull(ctx.NOT() != null);
        super.enterNullNotnull(ctx);
    }

    @Override
    public void enterAutoIncrementColumnConstraint(AutoIncrementColumnConstraintContext ctx) {
        columnEditor.autoIncremented(true);
        columnEditor.generated(true);
        super.enterAutoIncrementColumnConstraint(ctx);
    }

    @Override
    public void enterCommentColumnConstraint(CommentColumnConstraintContext ctx) {
        if (ctx.COMMENT() != null && ctx.STRING_LITERAL() != null) {
            columnEditor.comment(JdbcStringUtils.withoutWrapper(ctx.STRING_LITERAL().getText()));
        }
        super.enterCommentColumnConstraint(ctx);
    }

    @Override
    public void enterPrimaryKeyColumnConstraint(PrimaryKeyColumnConstraintContext ctx) {
        /**
         * sql example: `id` int NOT NULL AUTO_INCREMENT PRIMARY KEY,
         */
        ignoreColumn.set(false);
        this.tableEditor.withPrimaryKeyNames(this.columnEditor.ofName());
        super.enterPrimaryKeyColumnConstraint(ctx);
    }

    @Override
    public void enterUniqueKeyColumnConstraint(UniqueKeyColumnConstraintContext ctx) {
        ignoreColumn.set(false);
        super.enterUniqueKeyColumnConstraint(ctx);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void exitColumnDefinition(ColumnDefinitionContext ctx) {
        if (!ignoreColumn.get().booleanValue()) {
            this.tableEditor.addColumns(columnEditor.build());
            ignoreColumn.set(false);
        }

        // When exit column definition needs to remove DefaultValueParserListener from listener list
        parser.runIfAllNotNull(() -> listeners.remove(defaultValueParserListener), tableEditor);
        super.exitColumnDefinition(ctx);
    }

    @Override
    public void enterCollateColumnConstraint(CollateColumnConstraintContext ctx) {
        if (ctx.COLLATE() != null) {
            columnEditor.collate(ctx.collationName().getText());
        }
        super.enterCollateColumnConstraint(ctx);
    }
}
