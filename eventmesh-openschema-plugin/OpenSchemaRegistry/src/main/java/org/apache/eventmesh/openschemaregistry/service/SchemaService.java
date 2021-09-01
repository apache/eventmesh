package org.apache.eventmesh.openschemaregistry.service;

import org.apache.eventmesh.openschemaregistry.domain.Schema;
import org.apache.eventmesh.openschemaregistry.exception.NotFoundSchemaException;
import org.apache.eventmesh.openschemaregistry.exception.StorageServiceException;
import org.apache.eventmesh.openschemaregistry.repository.SchemaRepository;
import org.apache.eventmesh.openschemaregistry.response.SchemaResponse;
import org.apache.eventmesh.openschemaregistry.response.SubjectAndVersionResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SchemaService {

    @Autowired
    SchemaRepository schemaRepository;

    public SchemaResponse getSchemaById(long id){
        Schema schema = null;
        try {
            schema = schemaRepository.getSchemaById(id);
        }catch (Exception e){
            throw new StorageServiceException(HttpStatus.INTERNAL_SERVER_ERROR,"50001","Storage Service Error.");
        }
        if(schema==null){
            throw new NotFoundSchemaException(HttpStatus.NOT_FOUND,"40401","The corresponding schema information does not exist.");
        }
        SchemaResponse schemaResponse = new SchemaResponse();
        schemaResponse.setId(String.valueOf(schema.getId()));
        schemaResponse.setName(schema.getName());
        schemaResponse.setComment(schema.getComment());
        schemaResponse.setSerialization(schema.getSerialization());
        schemaResponse.setSchemaType(schema.getSchemaType());
        schemaResponse.setSchemaDefinition(schema.getSchemaDefinition());
        schemaResponse.setValidator(schema.getValidator());
        schemaResponse.setVersion(schema.getVersion());
        return schemaResponse;
    }

    public SubjectAndVersionResponse getSubjectAndVersionById(long id){
        Schema schema = null;
        try {
            schema = schemaRepository.getSchemaById(id);
        }catch (Exception e){
            throw new StorageServiceException(HttpStatus.INTERNAL_SERVER_ERROR,"50001","Storage Service Error.");
        }
        if(schema==null){
            throw new NotFoundSchemaException(HttpStatus.NOT_FOUND,"40401","The corresponding schema information does not exist.");
        }
        return new SubjectAndVersionResponse(schema.getSubject(), schema.getVersion());
    }
}
