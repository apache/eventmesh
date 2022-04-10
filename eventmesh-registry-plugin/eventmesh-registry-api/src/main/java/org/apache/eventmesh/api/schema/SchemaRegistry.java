package org.apache.eventmesh.api.schema;

import org.apache.eventmesh.common.schema.Schema;
import org.apache.eventmesh.common.schema.Subject;
import org.apache.eventmesh.spi.EventMeshExtensionType;
import org.apache.eventmesh.spi.EventMeshSPI;

/**
 * The interface of schema registry, used to register different schemas.
 * It should have multiple sub implementations, such as openschema etc.
 */
@EventMeshSPI(isSingleton = true, eventMeshExtensionType = EventMeshExtensionType.SCHEMAREGISTRY)
public interface SchemaRegistry {
    /**
     * start the schema registry
     */
    void start();

    /**
     * shutdown the schema registry
     */
    void shutdown();

    /**
     * register subject to schema registry
     *
     * @param subject the subject to be created
     */
    void registerSubject(Subject subject);

    /**
     * register schema to schema registry by subject
     *
     * @param subjectName name of the subject to be added a schema
     * @param schema      the schema to be added
     * @return the id of schema
     */
    int registerSchemaBySubject(String subjectName, Schema schema);

    /**
     * retrieve the latest schema by subject name
     *
     * @param subjectName the subject name
     * @return the latest schema
     */
    Schema retrieveLatestSchemaBySubject(String subjectName);

    /**
     * retrieve the schema by its id
     *
     * @param id the id of schema
     * @return the schema
     */
    Schema retrieveSchemaByID(int id);

    /**
     * retrieve the schema by subject and its version
     *
     * @param subjectName the name of the subject of desired schema
     * @param version     the version of desired schema
     * @return the schema
     */
    Schema retrieveSchemaBySubjectAndVersion(String subjectName, int version);
}
