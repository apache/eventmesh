package org.apache.eventmesh.connector.canal.source.table;

import org.apache.eventmesh.common.config.connector.rdb.canal.RdbTableDefinition;

import java.util.Objects;

import lombok.Data;

@Data
public class RdbSimpleTable extends RdbTableDefinition {
    public RdbSimpleTable(String database, String schema, String tableName) {
        this.schemaName = schema;
        this.tableName = tableName;
        this.database = database;
    }

    public RdbSimpleTable(String schema, String tableName) {
        this(null, schema, tableName);
    }

    private final String database;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        RdbSimpleTable that = (RdbSimpleTable) o;
        return Objects.equals(database, that.database);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), database);
    }
}
