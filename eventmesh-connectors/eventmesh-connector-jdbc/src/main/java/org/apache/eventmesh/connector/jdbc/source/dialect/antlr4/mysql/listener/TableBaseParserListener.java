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

import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.ColumnDeclarationContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.DottedIdContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.FullColumnNameContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.IndexColumnNamesContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.IndexOptionContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.PrimaryKeyTableConstraintContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.UidContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.UniqueKeyTableConstraintContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParserBaseListener;
import org.apache.eventmesh.connector.jdbc.source.dialect.antlr4.mysql.MysqlAntlr4DdlParser;
import org.apache.eventmesh.connector.jdbc.table.catalog.mysql.MysqlColumnEditor;
import org.apache.eventmesh.connector.jdbc.table.catalog.mysql.MysqlTableEditor;
import org.apache.eventmesh.connector.jdbc.utils.JdbcStringUtils;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.tree.ParseTreeListener;

public class TableBaseParserListener extends MySqlParserBaseListener {

    protected final List<ParseTreeListener> listeners;

    protected final MysqlAntlr4DdlParser parser;

    protected MysqlTableEditor tableEditor;

    protected ColumnDefinitionParserListener columnDefinitionListener;

    public TableBaseParserListener(List<ParseTreeListener> listeners, MysqlAntlr4DdlParser parser) {
        this.listeners = listeners;
        this.parser = parser;
    }

    /**
     * Called when entering a column declaration context.
     *
     * @param ctx The column declaration context.
     */
    @Override
    public void enterColumnDeclaration(ColumnDeclarationContext ctx) {

        /**
         * Parse
         * createDefinition
         *     : fullColumnName columnDefinition                               #columnDeclaration
         *     | tableConstraint NOT? ENFORCED?                                #constraintDeclaration
         *     | indexColumnDefinition                                         #indexDeclaration
         *     ;
         */
        parser.runIfAllNotNull(() -> {
            FullColumnNameContext fullColumnNameContext = ctx.fullColumnName();
            //parse column name
            UidContext uidContext = fullColumnNameContext.uid();
            List<DottedIdContext> dottedIdContextList = fullColumnNameContext.dottedId();
            if (CollectionUtils.isNotEmpty(dottedIdContextList)) {
                uidContext = dottedIdContextList.get(dottedIdContextList.size() - 1).uid();
            }
            String columnName = JdbcStringUtils.withoutWrapper(uidContext.getText());
            MysqlColumnEditor columnEditor = MysqlColumnEditor.ofEditor(columnName);
            if (Objects.isNull(columnDefinitionListener)) {
                columnDefinitionListener = new ColumnDefinitionParserListener(listeners, tableEditor, columnEditor, parser);
                //add ColumnDefinitionParserListener to listener list
                listeners.add(columnDefinitionListener);
            } else {
                columnDefinitionListener.setColumnEditor(columnEditor);
            }
        }, tableEditor);

        super.enterColumnDeclaration(ctx);
    }

    @Override
    public void exitColumnDeclaration(ColumnDeclarationContext ctx) {

        parser.runIfAllNotNull(() -> tableEditor.addColumns(columnDefinitionListener.getColumnEditor().build()), tableEditor,
            columnDefinitionListener);

        super.exitColumnDeclaration(ctx);
    }

    @Override
    public void enterPrimaryKeyTableConstraint(PrimaryKeyTableConstraintContext ctx) {
        /**
         *Although the creation of a Primary Key is defined within the column definitions when creating a table,
         * it can be considered as part of table management in practice.
         * SQL example: PRIMARY KEY (`id`),
         */
        parser.runIfAllNotNull(() -> {
            IndexColumnNamesContext indexColumnNamesContext = ctx.indexColumnNames();
            List<String> pkColumnNames = indexColumnNamesContext.indexColumnName().stream().map(indexColumnNameCtx -> {
                /**
                 * indexColumnName
                 *     : ((uid | STRING_LITERAL) ('(' decimalLiteral ')')? | expression) sortType=(ASC | DESC)?
                 *
                 */
                String pkColumnName;
                if (indexColumnNameCtx.uid() != null) {
                    pkColumnName = JdbcStringUtils.withoutWrapper(indexColumnNameCtx.uid().getText());
                } else if (indexColumnNameCtx.STRING_LITERAL() != null) {
                    pkColumnName = JdbcStringUtils.withoutWrapper(indexColumnNameCtx.STRING_LITERAL().getText());
                } else {
                    pkColumnName = indexColumnNameCtx.expression().getText();
                }
                return pkColumnName;
            }).collect(Collectors.toList());
            String comment = null;
            List<IndexOptionContext> indexOptionContexts = ctx.indexOption();
            for (IndexOptionContext indexOptionContext : indexOptionContexts) {
                if (indexOptionContext.COMMENT() != null && indexOptionContext.STRING_LITERAL() != null) {
                    comment = indexOptionContext.STRING_LITERAL().getText();
                }
            }
            tableEditor.withPrimaryKeyNames(pkColumnNames, comment);
        }, tableEditor);

        super.enterPrimaryKeyTableConstraint(ctx);
    }

    @Override
    public void enterUniqueKeyTableConstraint(UniqueKeyTableConstraintContext ctx) {

        //sql example: UNIQUE KEY `eventmesh` (`event_mesh`) USING BTREE COMMENT 'event mesh'

        parser.runIfAllNotNull(() -> {
            IndexColumnNamesContext indexColumnNamesContext = ctx.indexColumnNames();
            List<String> ukColumnNames = indexColumnNamesContext.indexColumnName().stream().map(indexColumnNameCtx -> {
                /**
                 * indexColumnName
                 *     : ((uid | STRING_LITERAL) ('(' decimalLiteral ')')? | expression) sortType=(ASC | DESC)?
                 *
                 */
                String ukColumnName;
                if (indexColumnNameCtx.uid() != null) {
                    ukColumnName = JdbcStringUtils.withoutWrapper(indexColumnNameCtx.uid().getText());
                } else if (indexColumnNameCtx.STRING_LITERAL() != null) {
                    ukColumnName = JdbcStringUtils.withoutWrapper(indexColumnNameCtx.STRING_LITERAL().getText());
                } else {
                    ukColumnName = indexColumnNameCtx.expression().getText();
                }
                return ukColumnName;
            }).collect(Collectors.toList());
            List<IndexOptionContext> indexOptionContexts = ctx.indexOption();
            String comment = null;
            if (CollectionUtils.isNotEmpty(indexOptionContexts)) {
                for (IndexOptionContext context : indexOptionContexts) {
                    if (context.COMMENT() != null && context.STRING_LITERAL() != null) {
                        comment = context.STRING_LITERAL().getText();
                    }
                }
            }
            String ukName = ctx.index != null ? ctx.index.getText() : null;
            tableEditor.withUniqueKeyColumnsNames(ukName, ukColumnNames, comment);
        }, tableEditor);

        super.enterUniqueKeyTableConstraint(ctx);
    }
}
