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

package org.apache.eventmesh.connector.jdbc.source.dialect.cdc.mysql;

import org.apache.eventmesh.common.EventMeshThreadFactory;
import org.apache.eventmesh.connector.jdbc.CatalogChanges;
import org.apache.eventmesh.connector.jdbc.DataChanges;
import org.apache.eventmesh.connector.jdbc.DataChanges.Builder;
import org.apache.eventmesh.connector.jdbc.Field;
import org.apache.eventmesh.connector.jdbc.Payload;
import org.apache.eventmesh.connector.jdbc.Schema;
import org.apache.eventmesh.connector.jdbc.config.JdbcConfig;
import org.apache.eventmesh.connector.jdbc.connection.mysql.MysqlJdbcConnection;
import org.apache.eventmesh.connector.jdbc.dialect.mysql.MysqlDatabaseDialect;
import org.apache.eventmesh.connector.jdbc.event.DeleteDataEvent;
import org.apache.eventmesh.connector.jdbc.event.EventConsumer;
import org.apache.eventmesh.connector.jdbc.event.GeneralDataChangeEvent;
import org.apache.eventmesh.connector.jdbc.event.InsertDataEvent;
import org.apache.eventmesh.connector.jdbc.event.SchemaChangeEventType;
import org.apache.eventmesh.connector.jdbc.event.UpdateDataEvent;
import org.apache.eventmesh.connector.jdbc.source.config.JdbcSourceConfig;
import org.apache.eventmesh.connector.jdbc.source.config.MysqlConfig;
import org.apache.eventmesh.connector.jdbc.source.dialect.antlr4.mysql.MysqlAntlr4DdlParser;
import org.apache.eventmesh.connector.jdbc.source.dialect.cdc.AbstractCdcEngine;
import org.apache.eventmesh.connector.jdbc.source.dialect.cdc.mysql.RowDeserializers.DeleteRowsEventMeshDeserializer;
import org.apache.eventmesh.connector.jdbc.source.dialect.cdc.mysql.RowDeserializers.UpdateRowsEventMeshDeserializer;
import org.apache.eventmesh.connector.jdbc.source.dialect.cdc.mysql.RowDeserializers.WriteRowsEventMeshDeserializer;
import org.apache.eventmesh.connector.jdbc.source.dialect.mysql.EventDataDeserializationExceptionData;
import org.apache.eventmesh.connector.jdbc.source.dialect.mysql.EventMeshGtidSet;
import org.apache.eventmesh.connector.jdbc.source.dialect.mysql.MysqlConstants;
import org.apache.eventmesh.connector.jdbc.source.dialect.mysql.MysqlJdbcContext;
import org.apache.eventmesh.connector.jdbc.source.dialect.mysql.MysqlSourceMateData;
import org.apache.eventmesh.connector.jdbc.table.catalog.Column;
import org.apache.eventmesh.connector.jdbc.table.catalog.DefaultValueConvertor;
import org.apache.eventmesh.connector.jdbc.table.catalog.TableId;
import org.apache.eventmesh.connector.jdbc.table.catalog.TableSchema;
import org.apache.eventmesh.connector.jdbc.table.catalog.mysql.MysqlDefaultValueConvertorImpl;
import org.apache.eventmesh.connector.jdbc.table.type.Pair;
import org.apache.eventmesh.openconnect.api.config.Config;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.github.shyiko.mysql.binlog.BinaryLogClient;
import com.github.shyiko.mysql.binlog.BinaryLogClient.LifecycleListener;
import com.github.shyiko.mysql.binlog.event.DeleteRowsEventData;
import com.github.shyiko.mysql.binlog.event.Event;
import com.github.shyiko.mysql.binlog.event.EventData;
import com.github.shyiko.mysql.binlog.event.EventHeader;
import com.github.shyiko.mysql.binlog.event.EventHeaderV4;
import com.github.shyiko.mysql.binlog.event.EventType;
import com.github.shyiko.mysql.binlog.event.GtidEventData;
import com.github.shyiko.mysql.binlog.event.QueryEventData;
import com.github.shyiko.mysql.binlog.event.RotateEventData;
import com.github.shyiko.mysql.binlog.event.TableMapEventData;
import com.github.shyiko.mysql.binlog.event.TransactionPayloadEventData;
import com.github.shyiko.mysql.binlog.event.UpdateRowsEventData;
import com.github.shyiko.mysql.binlog.event.WriteRowsEventData;
import com.github.shyiko.mysql.binlog.event.XidEventData;
import com.github.shyiko.mysql.binlog.event.deserialization.EventDataDeserializationException;
import com.github.shyiko.mysql.binlog.event.deserialization.EventDeserializer;
import com.github.shyiko.mysql.binlog.io.ByteArrayInputStream;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MysqlCdcEngine extends AbstractCdcEngine<MysqlAntlr4DdlParser, MysqlJdbcContext, MysqlDatabaseDialect> {

    private BinaryLogClient client;

    private BlockingQueue<Event> eventQueue = new LinkedBlockingQueue<>(10000);

    private final EnumMap<EventType, Consumer<Event>> eventHandlers = new EnumMap<>(EventType.class);

    private Map<Long/* table number */, TableId> tableIdMap = new HashMap<>(64);

    private MysqlJdbcContext context;

    private List<EventConsumer> consumers = new ArrayList<>(16);

    private MysqlAntlr4DdlParser ddlParser;

    private MysqlJdbcConnection connection;

    private com.github.shyiko.mysql.binlog.GtidSet localGtidSet;

    private DefaultValueConvertor defaultValueConvertor = new MysqlDefaultValueConvertorImpl();

    public MysqlCdcEngine(Config config, MysqlDatabaseDialect databaseDialect) {
        super((JdbcSourceConfig) config, databaseDialect);
        this.ddlParser = new MysqlAntlr4DdlParser(false, false, getHandledTables(), (JdbcSourceConfig) config);
        this.connection = databaseDialect.getConnection();
    }

    /**
     * Initializes the CDC Engine.
     */
    @Override
    public void init() {
        final JdbcConfig jdbcConfig = this.sourceConnectorConfig.getJdbcConfig();
        client = new BinaryLogClient(jdbcConfig.getHostname(), jdbcConfig.getPort(), jdbcConfig.getUser(), jdbcConfig.getPassword());
        client.setThreadFactory(new EventMeshThreadFactory("mysql-binlog-client"));
        final MysqlConfig mysqlConfig = this.sourceConnectorConfig.getMysqlConfig();
        client.setServerId(mysqlConfig.getServerId());
        client.setKeepAlive(mysqlConfig.isKeepAlive());
        final long keepAliveInterval = mysqlConfig.getKeepAliveInterval();
        client.setKeepAliveInterval(keepAliveInterval);

        final Map<Long, TableMapEventData> tableMapEventByTableId = new HashMap<>(32);

        // mysql dev url:https://dev.mysql.com/doc/dev/mysql-server/latest/
        EventDeserializer eventDeserializer = new EventDeserializer() {

            /**
             * @param inputStream input stream to fetch event from
             * @return deserialized event or null in case of end-of-stream
             * @throws IOException if connection gets closed
             */
            @Override
            public Event nextEvent(ByteArrayInputStream inputStream) throws IOException {
                try {
                    // Delegate to the superclass
                    Event event = super.nextEvent(inputStream);
                    log.debug("MYSQL Binlog---EventType={}, EventData={}", event.getHeader().getEventType(), event);
                    // We have to record the most recent TableMapEventData for each table number for our custom deserializers
                    if (event.getHeader().getEventType() == EventType.TABLE_MAP) {
                        TableMapEventData tableMapEvent = event.getData();
                        tableMapEventByTableId.put(tableMapEvent.getTableId(), tableMapEvent);
                    }

                    // Handle for transaction payload and capture the table map event and add it to the map
                    if (event.getHeader().getEventType() == EventType.TRANSACTION_PAYLOAD) {
                        TransactionPayloadEventData transactionPayloadEventData = event.getData();
                        /**
                         * Loop over the uncompressed events in the transaction payload event and add the table map
                         * event in the map of table events
                         **/
                        for (Event uncompressedEvent : transactionPayloadEventData.getUncompressedEvents()) {
                            if (uncompressedEvent.getHeader().getEventType() == EventType.TABLE_MAP && uncompressedEvent.getData() != null) {
                                TableMapEventData tableMapEvent = uncompressedEvent.getData();
                                tableMapEventByTableId.put(tableMapEvent.getTableId(), tableMapEvent);
                            }
                        }
                    }
                    // Clean cache on rotate event to prevent it from growing indefinitely.
                    if (event.getHeader().getEventType() == EventType.ROTATE) {
                        tableMapEventByTableId.clear();
                    }
                    return event;
                } catch (EventDataDeserializationException ex) {
                    if (ex.getCause() instanceof IOException) {
                        throw ex;
                    }
                    EventHeaderV4 header = new EventHeaderV4();
                    header.setEventType(EventType.INCIDENT);
                    header.setTimestamp(ex.getEventHeader().getTimestamp());
                    header.setServerId(ex.getEventHeader().getServerId());
                    if (ex.getEventHeader() instanceof EventHeaderV4) {
                        header.setEventLength(((EventHeaderV4) ex.getEventHeader()).getEventLength());
                        header.setNextPosition(((EventHeaderV4) ex.getEventHeader()).getNextPosition());
                        header.setFlags(((EventHeaderV4) ex.getEventHeader()).getFlags());
                    }
                    EventData data = new EventDataDeserializationExceptionData(ex);
                    return new Event(header, data);
                }
            }
        };

        eventDeserializer.setEventDataDeserializer(EventType.WRITE_ROWS, new WriteRowsEventMeshDeserializer(tableMapEventByTableId));
        eventDeserializer.setEventDataDeserializer(EventType.UPDATE_ROWS, new UpdateRowsEventMeshDeserializer(tableMapEventByTableId));
        eventDeserializer.setEventDataDeserializer(EventType.DELETE_ROWS, new DeleteRowsEventMeshDeserializer(tableMapEventByTableId));
        eventDeserializer.setEventDataDeserializer(EventType.EXT_WRITE_ROWS,
            new WriteRowsEventMeshDeserializer(tableMapEventByTableId).setMayContainExtraInformation(true));
        eventDeserializer.setEventDataDeserializer(EventType.EXT_UPDATE_ROWS,
            new UpdateRowsEventMeshDeserializer(tableMapEventByTableId).setMayContainExtraInformation(true));
        eventDeserializer.setEventDataDeserializer(EventType.EXT_DELETE_ROWS,
            new DeleteRowsEventMeshDeserializer(tableMapEventByTableId).setMayContainExtraInformation(true));

        // Set the event deserializer for the MySQL client
        client.setEventDeserializer(eventDeserializer);
        // Register an event listener for the MySQL client
        client.registerEventListener((event) -> eventMeshMysqlEventListener(event, context));
        // Register a lifecycle listener for the MySQL client
        client.registerLifecycleListener(new LifecycleListener() {

            @Override
            public void onConnect(BinaryLogClient client) {
                log.info("Client connect MySQL Server success");
            }

            @Override
            public void onCommunicationFailure(BinaryLogClient client, Exception ex) {
                log.error("Communicate with mysql error", ex);
            }

            @Override
            public void onEventDeserializationFailure(BinaryLogClient client, Exception ex) {
                log.error("Event deserialization failure", ex);
            }

            @Override
            public void onDisconnect(BinaryLogClient client) {
                log.info("Disconnect Mysql");
            }
        });

        // Register custom event handlers...
        eventHandlers.put(EventType.STOP, event -> handleStopEvent(context, event));
        eventHandlers.put(EventType.HEARTBEAT, event -> handleHeartbeatEvent(context, event));
        eventHandlers.put(EventType.INCIDENT, event -> handleServerIncident(context, event));
        eventHandlers.put(EventType.ROTATE, event -> handleRotateEvent(context, event));
        eventHandlers.put(EventType.TABLE_MAP, event -> handleTableMapEvent(context, event));
        eventHandlers.put(EventType.QUERY, event -> handleQueryEvent(context, event));
        eventHandlers.put(EventType.TRANSACTION_PAYLOAD, event -> handleTransactionPayload(context, event));

        // Used to support 5.1.16 - mysql-trunk
        eventHandlers.put(EventType.WRITE_ROWS, event -> handleInsertEvent(context, event));
        eventHandlers.put(EventType.UPDATE_ROWS, event -> handleUpdateEvent(context, event));
        eventHandlers.put(EventType.DELETE_ROWS, event -> handleDeleteEvent(context, event));

        // Used in case of RBR (5.1.18+).
        eventHandlers.put(EventType.EXT_WRITE_ROWS, event -> handleInsertEvent(context, event));
        eventHandlers.put(EventType.EXT_UPDATE_ROWS, event -> handleUpdateEvent(context, event));
        eventHandlers.put(EventType.EXT_DELETE_ROWS, event -> handleDeleteEvent(context, event));

        eventHandlers.put(EventType.VIEW_CHANGE, (event) -> handleViewChangeEvent(context, event));
        eventHandlers.put(EventType.XA_PREPARE, (event) -> handleXAPrepareTransactionEvent(context, event));
        eventHandlers.put(EventType.XID, (event) -> handleTransactionCompletionEvent(context, event));
    }

    public EventMeshGtidSet filterGtidSet(MysqlJdbcContext offsetContext, EventMeshGtidSet availableServerEventMeshGtidSet,
        EventMeshGtidSet purgedServerGtid) {
        String gtidStr = offsetContext.getGtidSet();
        if (gtidStr == null) {
            return null;
        }

        EventMeshGtidSet filteredEventMeshGtidSet = new EventMeshGtidSet(gtidStr);

        final EventMeshGtidSet knownEventMeshGtidSet = filteredEventMeshGtidSet;
        final EventMeshGtidSet relevantAvailableServerEventMeshGtidSet = availableServerEventMeshGtidSet;

        EventMeshGtidSet mergedEventMeshGtidSet = relevantAvailableServerEventMeshGtidSet
            .retainAll(uuid -> knownEventMeshGtidSet.forServerWithId(uuid) != null)
            .with(purgedServerGtid)
            .with(filteredEventMeshGtidSet);

        return mergedEventMeshGtidSet;
    }

    /**
     * handles events from the MySQL
     *
     * @param event   mysql binlog event
     * @param context mysql context
     */
    private void eventMeshMysqlEventListener(Event event, MysqlJdbcContext context) {

        if (event == null) {
            return;
        }
        final EventHeader eventHeader = event.getHeader();
        final EventType eventType = eventHeader.getEventType();
        /**
         * @see <a href="https://dev.mysql.com/doc/dev/mysql-server/latest/page_protocol_replication_binlog_event.html#sect_protocol_replication_event_rotate">ROTATE_EVENT</a>
         * +----------------------------------------------------+
         * |                ROTATE_EVENT                        |
         * +----------------------------------------------------+
         * |    Event Header    |    Position Info   |  Filename|
         * +----------------------------------------------------+
         * The rotate event is added to the binlog as last event to tell the reader what binlog to request next.
         */
        if (eventType == EventType.ROTATE) {
            RotateEventData rotateEventData = unwrapData(event);
            context.setBinlogStartPoint(rotateEventData.getBinlogFilename(), rotateEventData.getBinlogPosition());
        } else if (eventHeader instanceof EventHeaderV4) {
            EventHeaderV4 eventHeaderV4 = (EventHeaderV4) eventHeader;
            context.setEventPosition(eventHeaderV4.getPosition(), eventHeaderV4.getEventLength());
        }
        if (eventType == EventType.HEARTBEAT) {
            return;
        }
        try {
            eventQueue.put(event);
        } catch (InterruptedException e) {
            log.warn("Put event to queue error", e);
        }
        context.complete();
    }

    @Override
    public String getThreadName() {
        return "MySQL-CdcEngine";
    }

    /**
     * Returns the name of the CDC Engine.
     *
     * @return String representing the name of the CDC Engine.
     */
    @Override
    public String getCdcEngineName() {
        return "MySQL CDC Engine";
    }

    @Override
    public void close() throws Exception {
        client.disconnect();
    }

    /**
     * When an object implementing interface <code>Runnable</code> is used to create a thread, starting the thread causes the object's
     * <code>run</code> method to be called in that separately executing
     * thread.
     * <p>
     * The general contract of the method <code>run</code> is that it may take any action whatsoever.
     *
     * @see Thread#run()
     */
    @Override
    public void run() {
        enableGtidHandle();
        do {
            try {
                client.connect(TimeUnit.SECONDS.toMillis(5));
            } catch (IOException | TimeoutException e) {
                log.error("Binary log client connect to mysql server error, The connection will be retried in three seconds", e);
                await(3, TimeUnit.SECONDS);
            }
        } while (!client.isConnected());

        while (isRunning) {
            Event event = null;
            try {
                event = eventQueue.poll(5, TimeUnit.SECONDS);
                if (event == null) {
                    continue;
                }
                eventHandlers.getOrDefault(event.getHeader().getEventType(), ignore -> ignoreEvent(context, ignore)).accept(event);
            } catch (Exception e) {
                if (event != null) {
                    log.warn("Handle EventType={} error", event.getHeader().getEventType(), e);
                }
            }
        }
    }

    private void enableGtidHandle() {
        // Query whether mysql supports GTID
        if (this.connection.enableGTID()) {
            eventHandlers.put(EventType.GTID, event -> handleGtidEvent(context, event));

            // Query GtidSet from the MySQL Server
            String availableServerGtid = this.connection.executedGTID();
            EventMeshGtidSet executedEventMeshGtidSet = new EventMeshGtidSet(availableServerGtid);

            // Get purged GTID
            String purgedServerGtid = this.connection.purgedGTID();
            EventMeshGtidSet purgedServerEventMeshGtidSet = new EventMeshGtidSet(purgedServerGtid);

            EventMeshGtidSet filteredEventMeshGtidSet = filterGtidSet(context, executedEventMeshGtidSet, purgedServerEventMeshGtidSet);
            if (filteredEventMeshGtidSet != null) {
                client.setGtidSet(filteredEventMeshGtidSet.toString());
                this.context.completedGtidSet(filteredEventMeshGtidSet.toString());
                localGtidSet = new com.github.shyiko.mysql.binlog.GtidSet(filteredEventMeshGtidSet.toString());
            } else {
                client.setBinlogFilename(this.context.getSourceInfo().getCurrentBinlogFileName());
                client.setBinlogPosition(this.context.getSourceInfo().getCurrentBinlogPosition());
                localGtidSet = new com.github.shyiko.mysql.binlog.GtidSet("");
            }
        } else {
            client.setBinlogFilename(this.context.getSourceInfo().getCurrentBinlogFileName());
            client.setBinlogPosition(this.context.getSourceInfo().getCurrentBinlogPosition());
        }
    }

    /**
     * Handles the STOP_EVENT
     *
     * @param context the MySQL context
     * @param event   the event to be handled
     */
    protected void handleStopEvent(MysqlJdbcContext context, Event event) {
        // The purpose of STOP_EVENT is to inform MySQL that the slave or replication client has reached the end of the binary log
        // and no new events will be generated. When the slave receives a STOP_EVENT, it can take appropriate actions based on its needs,
        // such as closing the connection to the master server or reconnecting to obtain a new binary log file.
        log.debug("Replication client has reached the end of the binary log: {}", event);
    }

    /**
     * Handles the HEARTBEAT_EVENT
     *
     * @param context the MySQL context
     * @param event   the event to be handled
     */
    protected void handleHeartbeatEvent(MysqlJdbcContext context, Event event) {
        log.debug("Replication client handle {}", event.getHeader().getEventType());
    }

    /**
     * Handles the <a href="https://dev.mysql.com/doc/dev/mysql-server/latest/classbinary__log_1_1Incident__event.html">INCIDENT_EVENT</a>
     *
     * @param context the MySQL context
     * @param event   the event to be handled
     */
    protected void handleServerIncident(MysqlJdbcContext context, Event event) {

        final EventData eventData = event.getData();
        if (eventData instanceof EventDataDeserializationExceptionData) {
            log.error("Server incident: {}", event);
        }

    }

    /**
     * Handles the ROTATE_EVENT
     *
     * @param context the MySQL context
     * @param event   the event to be handled
     */
    protected void handleRotateEvent(MysqlJdbcContext context, Event event) {
        RotateEventData eventData = unwrapData(event);
        assert eventData != null;
        tableIdMap.clear();
    }

    @SuppressWarnings("unchecked")
    protected <T extends EventData> T unwrapData(Event event) {
        EventData eventData = event.getData();
        if (eventData instanceof EventDeserializer.EventDataWrapper) {
            eventData = ((EventDeserializer.EventDataWrapper) eventData).getInternal();
        }
        return (T) eventData;
    }

    /**
     * Handles the TABLE_MAP_EVENT Format @see <a
     * href="https://dev.mysql.com/doc/dev/mysql-server/latest/classbinary__log_1_1Table__map__event.html">TABLE_MAP_EVENT</a>
     *
     * @param context the MySQL context
     * @param event   the event to be handled
     */
    protected void handleTableMapEvent(MysqlJdbcContext context, Event event) {
        TableMapEventData tableMapEventData = event.getData();
        final long tableId = tableMapEventData.getTableId();
        final String tableName = tableMapEventData.getTable();
        final String database = tableMapEventData.getDatabase();
        tableIdMap.put(tableId, new TableId(database, null, tableName));
    }

    /**
     * Handles the QUERY_EVENT
     *
     * @param context mysql context
     * @param event   query event
     */
    protected void handleQueryEvent(MysqlJdbcContext context, Event event) {
        QueryEventData queryEventData = unwrapData(event);
        final String sql = queryEventData.getSql().trim();
        log.debug("Received query event SQL:{}", sql);
        if (StringUtils.equalsIgnoreCase("BEGIN", sql)) {
            // start transaction
            context.startTransaction();
            return;
        }

        if (StringUtils.equalsIgnoreCase("COMMIT", sql)) {
            context.commitTransaction();
            return;
        }

        if (StringUtils.startsWithIgnoreCase("XA", sql)) {
            // TODO: next version support

            return;
        }
        String sqlBegin = sql.substring(0, 6).toUpperCase();
        if (StringUtils.startsWithAny(sqlBegin, "INSERT", "UPDATE", "DELETE")) {
            log.warn("Received DML '[SQL={}]' for processing, binlog probably contains events generated with statement", sql);
            return;
        }

        // set current parse database to Ddl parser
        ddlParser.setCurrentDatabase(queryEventData.getDatabase());
        ddlParser.setCatalogTableSet(context.getCatalogTableSet());
        ddlParser.parse(sql, this::handleDdlEvent);
    }

    private void handleDdlEvent(org.apache.eventmesh.connector.jdbc.event.Event event) {
        if (event == null) {
            return;
        }
        // handle default value expression
        if (event.getJdbcConnectData().isSchemaChanges()) {
            CatalogChanges catalogChanges = event.getJdbcConnectData().getPayload().getCatalogChanges();
            SchemaChangeEventType schemaChangeEventType = SchemaChangeEventType.ofSchemaChangeEventType(catalogChanges.getType(),
                catalogChanges.getOperationType());
            if (SchemaChangeEventType.TABLE_CREATE == schemaChangeEventType || SchemaChangeEventType.TABLE_ALERT == schemaChangeEventType) {
                catalogChanges.getColumns().forEach(column -> {
                    column.setDefaultValue(defaultValueConvertor.parseDefaultValue(column, column.getDefaultValueExpression()));
                });
            }
        }
        event.getJdbcConnectData().getPayload().ofSourceMateData().setSnapshot(false);
        consumers.stream().forEach(consumer -> consumer.accept(event));
    }

    /**
     * Handles the TRANSACTION_PAYLOAD_EVENT
     * <p>
     * "binlog_transaction_compression" is a new feature introduced in MySQL 8.0.23, used for compressing transactions in the binary log (binlog).
     * This event is a wrapper event and encloses many other events.It is mostly used for carrying compressed payloads as its content can be
     * compressed, in which case, its metadata shall contain information about the compression metadata as well.
     *
     * </p>
     *
     * @param context the MySQL context
     * @param event   the event to be handled
     */
    protected void handleTransactionPayload(MysqlJdbcContext context, Event event) {

        TransactionPayloadEventData transactionPayloadEventData = event.getData();
        // unpack Event and handle
        ArrayList<Event> uncompressedEvents = transactionPayloadEventData.getUncompressedEvents();
        for (Event uncompressedEvent : uncompressedEvents) {
            final EventType eventType = uncompressedEvent.getHeader().getEventType();
            eventHandlers.getOrDefault(eventType, et -> ignoreEvent(context, et)).accept(uncompressedEvent);
        }
    }

    /**
     * Handles the insert event.
     *
     * @param context The MySQL context.
     * @param event   The insert event.
     */
    protected void handleInsertEvent(MysqlJdbcContext context, Event event) {
        WriteRowsEventData writeRowsEventData = unwrapData(event);
        log.debug("Received Write rows event, TableId={}", writeRowsEventData.getTableId());
        long tableNumber = writeRowsEventData.getTableId();
        TableId tableId = tableIdMap.get(tableNumber);

        if (!getHandledTables().contains(tableId)) {
            log.warn("Write rows-Table {} is excluded", tableId);
            return;
        }
        MysqlSourceMateData sourceMateData = buildMysqlSourceMateData(context, event, tableId);
        List<Serializable[]> insertRows = writeRowsEventData.getRows();
        if (CollectionUtils.isEmpty(insertRows)) {
            return;
        }
        List<Pair<Pair<Serializable[], BitSet>, Pair<Serializable[], BitSet>>> rows = new ArrayList<>();
        for (Serializable[] row : insertRows) {
            Pair<Serializable[], BitSet> item = new Pair<>(row, writeRowsEventData.getIncludedColumns());
            rows.add(new Pair<>(null, item));
        }
        handleCdcDmlData(context, sourceMateData, tableId, rows, CdcDmlType.INSERT);
    }

    private MysqlSourceMateData buildMysqlSourceMateData(MysqlJdbcContext context, Event event, TableId tableId) {
        MysqlSourceMateData sourceMateData = MysqlSourceMateData.newBuilder()
            .name(sourceConnectorConfig.getName())
            .withTableId(tableId)
            .serverId(sourceConnectorConfig.getMysqlConfig().getServerId())
            .binlogFile(context.getSourceInfo().getCurrentBinlogFileName())
            .position(((EventHeaderV4) event.getHeader()).getPosition())
            .build();
        return sourceMateData;
    }

    public enum CdcDmlType {
        INSERT,
        UPDATE,
        DELETE
    }

    private GeneralDataChangeEvent buildEvent(CdcDmlType type, TableId tableId) {
        switch (type) {
            case UPDATE:
                return new UpdateDataEvent(tableId);
            case INSERT:
                return new InsertDataEvent(tableId);
            case DELETE:
                return new DeleteDataEvent(tableId);
            default:
                return null;
        }
    }

    private void handleCdcDmlData(MysqlJdbcContext context, MysqlSourceMateData sourceMateData, TableId tableId,
        List<Pair<Pair<Serializable[], BitSet>, Pair<Serializable[], BitSet>>> rows, CdcDmlType type) {

        TableSchema tableSchema = context.getCatalogTableSet().getTableSchema(tableId);
        Map<Integer, ? extends Column<?>> orderColumnMap = tableSchema.getOrderColumnMap();
        List<? extends Column<?>> columns = tableSchema.getColumns();
        List<Field> fields = null;
        Builder builder = DataChanges.newBuilder();
        if (CollectionUtils.isNotEmpty(columns)) {
            fields = columns.stream()
                .map(col -> {
                    Column<?> rebuild = Column.newBuilder().withName(col.getName()).withDataType(col.getDataType()).withJdbcType(col.getJdbcType())
                        .withNativeType(col.getNativeType()).withOrder(col.getOrder()).build();
                    return new Field(rebuild, col.isNotNull(), col.getName(), tableId.toString());
                }).collect(Collectors.toList());
        }
        int columnsSize = orderColumnMap.size();
        for (Pair<Pair<Serializable[], BitSet>, Pair<Serializable[], BitSet>> pair : rows) {
            GeneralDataChangeEvent dataEvent = buildEvent(type, tableId);
            builder.withType(dataEvent.getDataChangeEventType().ofCode());
            Schema schema = new Schema();
            // set primary key
            schema.addKeys(tableSchema.getPrimaryKey().getColumnNames());
            Pair<Serializable[], BitSet> beforePair = Optional.ofNullable(pair.getLeft()).orElse(new Pair<>());
            Serializable[] beforeRows = beforePair.getLeft();
            if (beforeRows != null && beforeRows.length != 0) {
                BitSet includedColumns = beforePair.getRight();
                Map<String, Object> beforeValues = new HashMap<>(beforeRows.length);
                for (int index = 0; index < columnsSize; ++index) {
                    // Filter out empty fields
                    if (!includedColumns.get(index)) {
                        continue;
                    }
                    beforeValues.put(orderColumnMap.get(index + 1).getName(), beforeRows[index]);
                }
                builder.withBefore(beforeValues);
                Field beforeField = new Field().withField(Payload.BEFORE_FIELD).withName(Payload.PAYLOAD_BEFORE).withRequired(false);
                beforeField.withRequired(true).withFields(fields);
                schema.add(beforeField);
            }

            Pair<Serializable[], BitSet> afterPair = Optional.ofNullable(pair.getRight()).orElse(new Pair<>());
            Serializable[] afterRows = afterPair.getLeft();
            if (afterRows != null && afterRows.length != 0) {
                BitSet includedColumns = afterPair.getRight();
                Map<String, Object> afterValues = new HashMap<>(afterRows.length);
                for (int index = 0; index < columnsSize; ++index) {
                    // Filter out empty fields
                    if (!includedColumns.get(index)) {
                        continue;
                    }
                    afterValues.put(orderColumnMap.get(index + 1).getName(), afterRows[index]);
                }
                builder.withAfter(afterValues);
                Field afterField = new Field().withField(Payload.AFTER_FIELD).withName(Payload.PAYLOAD_AFTER).withRequired(false);
                afterField.withRequired(true).withFields(fields);
                schema.add(afterField);
            }
            Payload payload = dataEvent.getJdbcConnectData().getPayload();
            payload.withSource(sourceMateData).withDataChanges(builder.build());
            dataEvent.getJdbcConnectData().setSchema(schema);
            consumers.stream().forEach(consumer -> consumer.accept(dataEvent));
        }
    }

    /**
     * Handles the update event.
     *
     * @param context The MySQL context.
     * @param event   The update event.
     */
    protected void handleUpdateEvent(MysqlJdbcContext context, Event event) {
        UpdateRowsEventData updateRowsEventData = unwrapData(event);
        log.debug("Received Update rows event, Update table is {}", tableIdMap.get(updateRowsEventData.getTableId()));
        long id = updateRowsEventData.getTableId();
        TableId tableId = tableIdMap.get(id);
        if (!getHandledTables().contains(tableId)) {
            log.debug("Update rows-Table {} is excluded", tableId);
            return;
        }
        MysqlSourceMateData sourceMateData = buildMysqlSourceMateData(context, event, tableId);
        List<Entry<Serializable[], Serializable[]>> updateRows = updateRowsEventData.getRows();
        if (CollectionUtils.isEmpty(updateRows)) {
            return;
        }
        List<Pair<Pair<Serializable[], BitSet>, Pair<Serializable[], BitSet>>> rows = new ArrayList<>();
        for (Entry<Serializable[], Serializable[]> row : updateRows) {
            Pair<Serializable[], BitSet> before = new Pair<>(row.getKey(), updateRowsEventData.getIncludedColumnsBeforeUpdate());
            Pair<Serializable[], BitSet> after = new Pair<>(row.getValue(), updateRowsEventData.getIncludedColumns());
            rows.add(new Pair<>(before, after));
        }
        handleCdcDmlData(context, sourceMateData, tableId, rows, CdcDmlType.UPDATE);
    }

    /**
     * Handles the delete event.
     *
     * @param context The MySQL context.
     * @param event   The delete event.
     */
    protected void handleDeleteEvent(MysqlJdbcContext context, Event event) {
        DeleteRowsEventData deleteRowsEventData = unwrapData(event);
        log.debug("Received Delete rows event, Delete table is {}", tableIdMap.get(deleteRowsEventData.getTableId()));
        long id = deleteRowsEventData.getTableId();
        TableId tableId = tableIdMap.get(id);

        if (!getHandledTables().contains(tableId)) {
            log.debug("Update rows-Table {} is excluded", tableId);
            return;
        }
        MysqlSourceMateData sourceMateData = buildMysqlSourceMateData(context, event, tableId);
        List<Serializable[]> deleteRows = deleteRowsEventData.getRows();
        if (CollectionUtils.isEmpty(deleteRows)) {
            return;
        }
        List<Pair<Pair<Serializable[], BitSet>, Pair<Serializable[], BitSet>>> rows = new ArrayList<>();
        for (Serializable[] row : deleteRows) {
            Pair<Serializable[], BitSet> item = new Pair<>(row, deleteRowsEventData.getIncludedColumns());
            rows.add(new Pair<>(item, null));
        }
        handleCdcDmlData(context, sourceMateData, tableId, rows, CdcDmlType.DELETE);
    }

    /**
     * Handles the GTID event.
     *
     * @param context The MySQL context.
     * @param event   The GTID event.
     */
    protected void handleGtidEvent(MysqlJdbcContext context, Event event) {
        GtidEventData gtidEvent = unwrapData(event);
        String gtid = gtidEvent.getMySqlGtid().toString();
        log.debug("Received GTID event: {}", gtid);
        localGtidSet.add(gtid);
        context.beginGtid(gtid);
    }

    /**
     * Handles the view change event.
     *
     * @param context The MySQL context.
     * @param event   The view change event.
     */
    protected void handleViewChangeEvent(MysqlJdbcContext context, Event event) {
        // TODO: Add support for handling view change event
    }

    /**
     * Handles the XA prepare transaction event.
     *
     * @param context The MySQL context.
     * @param event   The XA prepare transaction event.
     */
    protected void handleXAPrepareTransactionEvent(MysqlJdbcContext context, Event event) {
        // TODO: Add support for handling XA prepare transaction event
    }

    /**
     * Handles the transaction completion event.
     *
     * @param context The MySQL context.
     * @param event   The transaction completion event.
     */
    protected void handleTransactionCompletionEvent(MysqlJdbcContext context, Event event) {
        XidEventData xidEventData = unwrapData(event);
        log.debug("Received XID event, Xid={}", xidEventData.getXid());
        context.commitTransaction();
    }

    /**
     * Default handler that ignores events.
     *
     * @param context The MySQL context.
     * @param event   The event to be ignored.
     */
    protected void ignoreEvent(MysqlJdbcContext context, Event event) {
        log.debug("Ignoring event due to missing handler: {}", event);
    }

    @Override
    public void registerCdcEventConsumer(EventConsumer consumer) {
        if (consumer == null) {
            return;
        }
        consumers.add(consumer);
    }

    @Override
    protected Set<String> defaultExcludeDatabase() {
        return MysqlConstants.DEFAULT_EXCLUDE_DATABASE;
    }

    @Override
    protected MysqlAntlr4DdlParser getDdlParser() {
        return ddlParser;
    }

    @Override
    public void setContext(MysqlJdbcContext context) {
        if (context == null) {
            context = MysqlJdbcContext.initialize(this.jdbcSourceConfig);
        }
        this.context = context;
    }
}
