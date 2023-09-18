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

import org.apache.eventmesh.connector.jdbc.antlr4.listener.Antlr4DdlParserListener;
import org.apache.eventmesh.connector.jdbc.source.dialect.antlr4.mysql.MysqlAntlr4DdlParser;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.TerminalNode;

public class MySqlAntlr4DdlParserListener implements Antlr4DdlParserListener {

    private final List<ParseTreeListener> listeners = new CopyOnWriteArrayList<>();

    public MySqlAntlr4DdlParserListener(MysqlAntlr4DdlParser parser) {
        listeners.add(new CreateDatabaseParserListener(parser));
        listeners.add(new DropDatabaseParserListener(parser));
        listeners.add(new CreateTableParserListener(listeners, parser));
        listeners.add(new TruncateTableParserListener(parser));
    }

    @Override
    public void visitTerminal(TerminalNode node) {
        for (ParseTreeListener listener : listeners) {
            listener.visitTerminal(node);
        }
    }

    @Override
    public void visitErrorNode(ErrorNode node) {
        for (ParseTreeListener listener : listeners) {
            listener.visitErrorNode(node);
        }
    }

    @Override
    public void enterEveryRule(ParserRuleContext ctx) {

        for (ParseTreeListener listener : listeners) {
            listener.enterEveryRule(ctx);
            ctx.enterRule(listener);
        }
    }

    @Override
    public void exitEveryRule(ParserRuleContext ctx) {
        for (ParseTreeListener listener : listeners) {
            ctx.exitRule(listener);
            listener.exitEveryRule(ctx);
        }
    }
}
