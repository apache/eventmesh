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
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.CollectionDataTypeContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.ColumnDefinitionContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.CommentColumnConstraintContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.DataTypeContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.DecimalLiteralContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.DimensionDataTypeContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.LongVarcharDataTypeContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.NationalStringDataTypeContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.NationalVaryingStringDataTypeContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.NullNotnullContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.PrimaryKeyColumnConstraintContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.SimpleDataTypeContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.SpatialDataTypeContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.StringDataTypeContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.UniqueKeyColumnConstraintContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParserBaseListener;
import org.apache.eventmesh.connector.jdbc.source.dialect.antlr4.mysql.MysqlAntlr4DdlParser;
import org.apache.eventmesh.connector.jdbc.source.dialect.mysql.MysqlDataTypeConvertor;
import org.apache.eventmesh.connector.jdbc.table.catalog.TableEditor;
import org.apache.eventmesh.connector.jdbc.table.catalog.mysql.MysqlColumnEditor;
import org.apache.eventmesh.connector.jdbc.table.catalog.mysql.MysqlOptions.MysqlColumnOptions;
import org.apache.eventmesh.connector.jdbc.table.type.EventMeshDataType;
import org.apache.eventmesh.connector.jdbc.utils.JdbcStringUtils;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.tree.ParseTreeListener;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Setter
@Slf4j
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
            DataTypeContext dataTypeContext = ctx.dataType();
            String dataTypeString = null;
            if (dataTypeContext instanceof StringDataTypeContext) {
                StringDataTypeContext stringDataTypeCtx = (StringDataTypeContext) dataTypeContext;
                dataTypeString = stringDataTypeCtx.typeName.getText();
                // parse data type length
                if (stringDataTypeCtx.lengthOneDimension() != null) {
                    this.columnEditor.length(Integer.parseInt(stringDataTypeCtx.lengthOneDimension().decimalLiteral().getText()));
                }
                // parse data type charset and collation
                String charsetName = parser.parseCharset(stringDataTypeCtx.charsetName());
                String collationName = parser.parseCollation(stringDataTypeCtx.collationName());
                columnEditor.charsetName(charsetName);
                columnEditor.collation(collationName);
            } else if (dataTypeContext instanceof NationalStringDataTypeContext) {
                NationalStringDataTypeContext nationalStringDataTypeCtx = (NationalStringDataTypeContext) dataTypeContext;
                dataTypeString = nationalStringDataTypeCtx.typeName.getText();
                if (nationalStringDataTypeCtx.lengthOneDimension() != null) {
                    this.columnEditor.length(Integer.parseInt(nationalStringDataTypeCtx.lengthOneDimension().decimalLiteral().getText()));
                }
            } else if (dataTypeContext instanceof NationalVaryingStringDataTypeContext) {
                NationalVaryingStringDataTypeContext nationalVaryingStringDataTypeCtx = (NationalVaryingStringDataTypeContext) dataTypeContext;
                dataTypeString = nationalVaryingStringDataTypeCtx.typeName.getText();
                if (nationalVaryingStringDataTypeCtx.lengthOneDimension() != null) {
                    this.columnEditor.length(Integer.parseInt(nationalVaryingStringDataTypeCtx.lengthOneDimension().decimalLiteral().getText()));
                }
            } else if (dataTypeContext instanceof DimensionDataTypeContext) {
                DimensionDataTypeContext dimensionDataTypeCtx = (DimensionDataTypeContext) dataTypeContext;
                dataTypeString = dimensionDataTypeCtx.typeName.getText();
                // parse column length
                if (dimensionDataTypeCtx.lengthOneDimension() != null) {
                    this.columnEditor.length(Integer.parseInt(dimensionDataTypeCtx.lengthOneDimension().decimalLiteral().getText()));
                }
                // parse column scale if has scale
                if (dimensionDataTypeCtx.lengthTwoDimension() != null) {
                    List<DecimalLiteralContext> decimalLiteralContexts = dimensionDataTypeCtx.lengthTwoDimension().decimalLiteral();
                    this.columnEditor.length(Integer.parseInt(decimalLiteralContexts.get(0).getText()));
                    this.columnEditor.scale(Integer.parseInt(decimalLiteralContexts.get(1).getText()));
                }

                if (dimensionDataTypeCtx.lengthTwoOptionalDimension() != null) {
                    List<DecimalLiteralContext> decimalLiteralContexts = dimensionDataTypeCtx.lengthTwoOptionalDimension().decimalLiteral();
                    if (decimalLiteralContexts.get(0).REAL_LITERAL() != null) {
                        String[] digits = decimalLiteralContexts.get(0).getText().split(".");
                        if (StringUtils.isBlank(digits[0]) || Integer.valueOf(digits[0]) == 0) {
                            this.columnEditor.length(10);
                        } else {
                            this.columnEditor.length(Integer.valueOf(digits[0]));
                        }
                    } else {
                        this.columnEditor.length(Integer.parseInt(decimalLiteralContexts.get(0).getText()));
                    }
                    if (decimalLiteralContexts.size() > 1) {
                        this.columnEditor.scale(Integer.parseInt(decimalLiteralContexts.get(1).getText()));
                    }
                }
                if (CollectionUtils.isNotEmpty(dimensionDataTypeCtx.SIGNED())) {
                    this.columnEditor.withOption(MysqlColumnOptions.SIGNED, dimensionDataTypeCtx.SIGNED().get(0).getText());
                }
                if (CollectionUtils.isNotEmpty(dimensionDataTypeCtx.UNSIGNED())) {
                    this.columnEditor.withOption(MysqlColumnOptions.UNSIGNED, dimensionDataTypeCtx.UNSIGNED().get(0).getText());
                }
                if (CollectionUtils.isNotEmpty(dimensionDataTypeCtx.ZEROFILL())) {
                    this.columnEditor.withOption(MysqlColumnOptions.ZEROFILL, dimensionDataTypeCtx.ZEROFILL().get(0).getText());
                }
            } else if (dataTypeContext instanceof SimpleDataTypeContext) {
                // Do nothing for example: DATE, TINYBLOB, etc.
                SimpleDataTypeContext simpleDataTypeCtx = (SimpleDataTypeContext) dataTypeContext;
                dataTypeString = simpleDataTypeCtx.typeName.getText();
            } else if (dataTypeContext instanceof CollectionDataTypeContext) {
                CollectionDataTypeContext collectionDataTypeContext = (CollectionDataTypeContext) dataTypeContext;
                dataTypeString = collectionDataTypeContext.typeName.getText();
                if (collectionDataTypeContext.charsetName() != null) {
                    String charsetName = collectionDataTypeContext.charsetName().getText();
                    columnEditor.charsetName(charsetName);
                }
            } else if (dataTypeContext instanceof SpatialDataTypeContext) {
                // do nothing
                SpatialDataTypeContext spatialDataTypeCtx = (SpatialDataTypeContext) dataTypeContext;
                dataTypeString = spatialDataTypeCtx.typeName.getText();
            } else if (dataTypeContext instanceof LongVarcharDataTypeContext) {
                LongVarcharDataTypeContext longVarcharDataTypeCtx = (LongVarcharDataTypeContext) dataTypeContext;
                dataTypeString = longVarcharDataTypeCtx.typeName.getText();
                String charsetName = parser.parseCharset(longVarcharDataTypeCtx.charsetName());
                String collationName = parser.parseCollation(longVarcharDataTypeCtx.collationName());
                columnEditor.charsetName(charsetName);
                columnEditor.collation(collationName);
            }
            // handle enum and set type values
            if (StringUtils.equalsAnyIgnoreCase(dataTypeString, "ENUM", "SET")) {
                CollectionDataTypeContext collectionDataTypeContext = (CollectionDataTypeContext) dataTypeContext;
                List<String> values = collectionDataTypeContext.collectionOptions().STRING_LITERAL().stream()
                    .map(node -> JdbcStringUtils.withoutWrapper(node.getText())).collect(Collectors.toList());
                columnEditor.enumValues(values);
            }

            if (StringUtils.isNotBlank(dataTypeString)) {
                EventMeshDataType eventMeshType = this.dataTypeConvertor.toEventMeshType(dataTypeString);
                this.columnEditor.withEventMeshType(eventMeshType);
                this.columnEditor.withJdbcType(this.dataTypeConvertor.toJDBCType(dataTypeString));
                this.columnEditor.withType(dataTypeString);
            }
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
            this.ignoreColumn.set(false);
        }

        // When exit column definition needs to remove DefaultValueParserListener from listener list
        parser.runIfAllNotNull(() -> listeners.remove(defaultValueParserListener), tableEditor);
        super.exitColumnDefinition(ctx);
    }

    @Override
    public void enterCollateColumnConstraint(CollateColumnConstraintContext ctx) {
        if (ctx.COLLATE() != null) {
            columnEditor.collation(ctx.collationName().getText());
        }
        super.enterCollateColumnConstraint(ctx);
    }
}
