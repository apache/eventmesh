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

package org.apache.eventmesh.connector.jdbc.source.dialect.antlr4.mysql;

import org.apache.eventmesh.connector.jdbc.antlr4.Antlr4DdlParser;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlLexer;
import org.apache.eventmesh.connector.jdbc.antlr4.autogeneration.MySqlParser;
import org.apache.eventmesh.connector.jdbc.antlr4.listener.Antlr4DdlParserListener;
import org.apache.eventmesh.connector.jdbc.ddl.DdlParserCallback;
import org.apache.eventmesh.connector.jdbc.event.Event;
import org.apache.eventmesh.connector.jdbc.source.config.JdbcSourceConfig;
import org.apache.eventmesh.connector.jdbc.source.dialect.antlr4.mysql.listener.MySqlAntlr4DdlParserListener;
import org.apache.eventmesh.connector.jdbc.table.catalog.TableId;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.Set;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

public class MysqlAntlr4DdlParser extends Antlr4DdlParser<MySqlLexer, MySqlParser> {

    private DdlParserCallback callback;

    private final Set<TableId> includeDatabaseTable;

    private final JdbcSourceConfig sourceConfig;

    public MysqlAntlr4DdlParser(boolean skipViews, boolean skipComments, Set<TableId> includeDatabaseTable, JdbcSourceConfig sourceConfig) {
        super(skipViews, skipComments);
        this.includeDatabaseTable = includeDatabaseTable;
        this.sourceConfig = sourceConfig;
    }

    public MysqlAntlr4DdlParser(boolean skipViews, boolean skipComments, JdbcSourceConfig sourceConfig) {
        this(skipViews, skipComments, new HashSet<>(), sourceConfig);
    }

    @Override
    protected MySqlLexer buildLexerInstance(CharStream charStreams) {
        return new MySqlLexer(charStreams);
    }

    @Override
    protected MySqlParser buildParserInstance(CommonTokenStream commonTokenStream) {
        return new MySqlParser(commonTokenStream);
    }

    @Override
    protected ParseTree parseTree(MySqlParser parser) {
        return parser.root();
    }

    @Override
    protected Antlr4DdlParserListener createParseTreeWalkerListener(DdlParserCallback callback) {
        this.callback = callback;
        return new MySqlAntlr4DdlParserListener(this);
    }


    /**
     * Runs the given EventMeshRunner if all the nullableObjects are not null.
     *
     * @param runner          the Runnable to be run
     * @param nullableObjects the objects to be checked for null
     */
    public void runIfAllNotNull(Runnable runner, Object... nullableObjects) {
        // If nullableObjects is null or empty, run the runner
        if (nullableObjects == null || nullableObjects.length == 0) {
            runner.run();
        }
        // Check each nullableObject for null
        for (Object nullableObject : nullableObjects) {
            // If any nullableObject is null, return without running the runner
            if (nullableObject == null) {
                return;
            }
        }
        // Run the runner if all nullableObjects are not null
        runner.run();
    }

    /**
     * Parses a table ID from the given full ID text.
     *
     * @param fullIdText The full ID text.
     * @return The parsed TableId object.
     */
    public TableId parseTableId(String fullIdText) {
        // Remove special characters from the full ID text
        String sanitizedText = StringUtils.replaceEach(fullIdText, new String[]{"'\\''", "\"", "`"}, new String[]{"", "", ""});

        // Split the sanitized text by dot (.) to separate catalog and table name
        String[] split = sanitizedText.split("\\.");

        TableId tableId = new TableId();

        // Set the table name if there is no catalog specified
        if (split.length == 1) {
            tableId.setTableName(split[0]);
        } else {
            // Set the catalog and table name if both are specified
            tableId.setCatalogName(split[0]);
            tableId.setTableName(split[1]);
        }

        return tableId;
    }

    public DdlParserCallback getCallback() {
        return callback;
    }

    public void handleEvent(Event event) {
        if (callback != null) {
            callback.handle(event);
        }
    }

    public boolean includeTableId(TableId tableId) {
        return includeDatabaseTable.contains(tableId);
    }

    public void addTableIdSet(Set<TableId> tableIdSet) {
        if (CollectionUtils.isEmpty(tableIdSet)) {
            return;
        }
        this.includeDatabaseTable.addAll(tableIdSet);
    }

    public JdbcSourceConfig getSourceConfig() {
        return sourceConfig;
    }
}
