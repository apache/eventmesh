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

package org.apache.eventmesh.connector.jdbc.antlr4;

import org.apache.eventmesh.connector.jdbc.antlr4.listener.Antlr4DdlParserListener;
import org.apache.eventmesh.connector.jdbc.ddl.AbstractDdlParser;
import org.apache.eventmesh.connector.jdbc.ddl.DdlParserCallback;
import org.apache.eventmesh.connector.jdbc.table.catalog.CatalogTableSet;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CodePointCharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

public abstract class Antlr4DdlParser<L extends Lexer, P extends Parser> extends AbstractDdlParser {

    private Antlr4DdlParserListener antlr4DdlParserListener;

    private CatalogTableSet tableSet;

    public Antlr4DdlParser(boolean skipViews, boolean skipComments) {
        super(skipViews, skipComments);
    }

    /**
     * Parses the given DDL SQL statement.
     *
     * @param ddlSql the DDL SQL statement to be parsed
     */
    @Override
    public void parse(String ddlSql, DdlParserCallback callback) {
        CodePointCharStream ddlSqlStream = CharStreams.fromString(ddlSql);
        L lexer = buildLexerInstance(ddlSqlStream);
        P parser = buildParserInstance(new CommonTokenStream(lexer));
        ParseTree parseTree = parseTree(parser);
        antlr4DdlParserListener = createParseTreeWalkerListener(callback);
        ParseTreeWalker.DEFAULT.walk(antlr4DdlParserListener, parseTree);
    }

    protected abstract L buildLexerInstance(CharStream charStreams);

    protected abstract P buildParserInstance(CommonTokenStream commonTokenStream);

    protected abstract ParseTree parseTree(P parser);

    protected abstract Antlr4DdlParserListener createParseTreeWalkerListener(DdlParserCallback callback);

    public void setCatalogTableSet(CatalogTableSet tableSet) {
        this.tableSet = tableSet;
    }

    public CatalogTableSet getCatalogTableSet() {
        return this.tableSet;
    }

}
