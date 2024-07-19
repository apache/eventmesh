package org.apache.eventmesh.common.config.connector.rdb.canal;

import java.util.Set;

import lombok.Data;

/**
 * Description: as class name
 */
@Data
public class RdbDBDefinition {
    private String schemaName;
    private Set<RdbTableDefinition> tables;
}
