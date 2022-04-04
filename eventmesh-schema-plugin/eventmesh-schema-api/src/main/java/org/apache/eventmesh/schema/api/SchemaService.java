package org.apache.eventmesh.schema.api;

import org.apache.eventmesh.common.schema.SchemaAgenda;
import org.apache.eventmesh.spi.EventMeshExtensionType;
import org.apache.eventmesh.spi.EventMeshSPI;

/**
 * Schema services should be implemented in plugins.
 */
@EventMeshSPI(isSingleton = true, eventMeshExtensionType = EventMeshExtensionType.SCHEMA)
public interface SchemaService {

    /**
     * contract
     */
    void contract();

    /**
     * serialization according to schema
     */
    void serialize();

    /**
     * deserialization from string according to schema
     */
    void deserialize();

    /**
     * check the validity of a content according to schema
     *
     * @param schemaAgenda a wrapper of content, content type, and schema
     * @return whether the content is valid
     */
    boolean checkSchemaValidity(SchemaAgenda schemaAgenda);
}
