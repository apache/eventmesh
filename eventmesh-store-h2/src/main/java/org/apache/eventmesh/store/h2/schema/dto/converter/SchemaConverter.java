package org.apache.eventmesh.store.h2.schema.dto.converter;

import org.apache.eventmesh.store.api.openschema.response.SchemaResponse;
import org.apache.eventmesh.store.api.openschema.response.SubjectResponse;
import org.apache.eventmesh.store.api.openschema.response.SubjectVersionResponse;
import org.apache.eventmesh.store.h2.schema.domain.Schema;
import org.apache.eventmesh.store.h2.schema.domain.Subject;

public class SchemaConverter {
    
	public SchemaConverter() {}
	
    public SchemaResponse toSchemaResponse(Schema schema) {
    	String version = String.valueOf(schema.getVersion());
    	SchemaResponse schemaResponse = new SchemaResponse(schema.getName(),schema.getComment(),
    			schema.getSerialization(),schema.getSchemaType(),schema.getSchemaDefinition(),
    			schema.getValidator(),version);
    	schemaResponse.setId(schema.getId());
        return schemaResponse;
    }
    
    public SubjectVersionResponse toSubjectVersionResponse(Schema schema) {    	
    	SubjectVersionResponse subjectVersionResponse = new SubjectVersionResponse(schema.getSubjectName(),schema.getVersion());    	
        return subjectVersionResponse;
    }
}
