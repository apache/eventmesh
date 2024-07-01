package org.apache.eventmesh.connector.canal.source.table;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.eventmesh.common.config.connector.rdb.canal.RdbTableDefinition;

import java.util.Objects;

@Data
public class RdbSimpleTable extends RdbTableDefinition {
    public RdbSimpleTable(String schema, String tableName) {
        super();
        this.schema = schema;
        this.tableName = tableName;
    }
    private String schema;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        RdbSimpleTable that = (RdbSimpleTable) o;
        return Objects.equals(schema, that.schema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), schema);
    }
}
