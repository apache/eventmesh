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

import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.CurrentTimestampContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.DefaultValueContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser.StringLiteralContext;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParserBaseListener;
import org.apache.eventmesh.connector.jdbc.table.catalog.mysql.MysqlColumnEditor;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

/**
 * Listener for parsing default values in MySQL parser.
 */
public class DefaultValueParserListener extends MySqlParserBaseListener {

    private final MysqlColumnEditor columnEditor;

    public DefaultValueParserListener(MysqlColumnEditor columnEditor) {
        this.columnEditor = columnEditor;
    }

    @Override
    public void enterDefaultValue(DefaultValueContext ctx) {

        /**
         * defaultValue
         *     : NULL_LITERAL
         *     | CAST '(' expression AS convertedDataType ')'
         *     | unaryOperator? constant
         *     | currentTimestamp (ON UPDATE currentTimestamp)?
         *     | '(' expression ')'
         *     | '(' fullId ')'
         *     ;
         */
        String sign = "";

        //Default value is NULL
        if (ctx.NULL_LITERAL() != null) {
            return;
        }

        if (ctx.CAST() != null && ctx.expression() != null) {
            columnEditor.defaultValueExpression(ctx.getText());
            return;
        }

        if (ctx.unaryOperator() != null) {
            sign = ctx.unaryOperator().getText();
        }

        /**
         * Process expression
         * constant
         *     : stringLiteral | decimalLiteral
         *     | '-' decimalLiteral
         *     | hexadecimalLiteral | booleanLiteral
         *     | REAL_LITERAL | BIT_STRING
         *     | NOT? nullLiteral=(NULL_LITERAL | NULL_SPEC_LITERAL)
         *     ;
         */
        if (ctx.constant() != null) {
            StringLiteralContext stringLiteralContext = ctx.constant().stringLiteral();
            if (stringLiteralContext != null) {
                if (stringLiteralContext.COLLATE() == null) {
                    columnEditor.defaultValueExpression(sign + unquote(stringLiteralContext.getText()));
                } else {
                    columnEditor.collate(sign + unquote(stringLiteralContext.STRING_LITERAL(0).getText()));
                }
            } else if (ctx.constant().decimalLiteral() != null) {
                columnEditor.defaultValueExpression(sign + ctx.constant().decimalLiteral().getText());
            } else if (ctx.constant().BIT_STRING() != null) {
                columnEditor.defaultValueExpression(unquoteBinary(ctx.constant().BIT_STRING().getText()));
            } else if (ctx.constant().booleanLiteral() != null) {
                columnEditor.defaultValueExpression(ctx.constant().booleanLiteral().getText());
            } else if (ctx.constant().REAL_LITERAL() != null) {
                columnEditor.defaultValueExpression(ctx.constant().REAL_LITERAL().getText());
            }
        } else if (CollectionUtils.isNotEmpty(ctx.currentTimestamp())) {

            /**
             * <a href="https://dev.mysql.com/doc/refman/8.0/en/timestamp-initialization.html">timestamp-initialization</a>
             * <a href="https://dev.mysql.com/doc/refman/8.0/en/cast-functions.html">cast-functions</a>
             * defaultValue
             *     : NULL_LITERAL
             *     | CAST '(' expression AS convertedDataType ')'
             *     | unaryOperator? constant
             *     | currentTimestamp (ON UPDATE currentTimestamp)?
             *     | '(' expression ')'
             *     | '(' fullId ')'
             *     ;
             * currentTimestamp
             *     :
             *     (
             *       (CURRENT_TIMESTAMP | LOCALTIME | LOCALTIMESTAMP)
             *       ('(' decimalLiteral? ')')?
             *       | NOW '(' decimalLiteral? ')'
             *     )
             *     ;
             */
            List<CurrentTimestampContext> currentTimestampContexts = ctx.currentTimestamp();
            if (currentTimestampContexts.size() > 1 || (ctx.ON() == null && ctx.UPDATE() == null)) {
                CurrentTimestampContext currentTimestampContext = currentTimestampContexts.get(0);
                //
                if (currentTimestampContext.CURRENT_TIMESTAMP() != null || currentTimestampContext.NOW() != null) {
                    columnEditor.defaultValueExpression("1970-01-01 00:00:00");
                } else {
                    columnEditor.defaultValueExpression(currentTimestampContext.getText());
                }
            }
        } else if (ctx.expression() != null) {
            //e.g. CREATE TABLE t2 (b BLOB DEFAULT ('abc'));
            columnEditor.defaultValueExpression(ctx.expression().getText());
        } else if (ctx.fullId() != null) {
            columnEditor.defaultValueExpression(ctx.expression().getText());
        }
        super.enterDefaultValue(ctx);
    }

    @Override
    public void exitDefaultValue(DefaultValueContext ctx) {
        super.exitDefaultValue(ctx);
    }

    private String unquote(String stringLiteral) {
        if (stringLiteral != null && ((stringLiteral.startsWith("'") && stringLiteral.endsWith("'"))
            || (stringLiteral.startsWith("\"") && stringLiteral.endsWith("\"")))) {
            return stringLiteral.substring(1, stringLiteral.length() - 1);
        }
        return stringLiteral;
    }

    private String unquoteBinary(String stringLiteral) {
        return stringLiteral.substring(2, stringLiteral.length() - 1);
    }
}
