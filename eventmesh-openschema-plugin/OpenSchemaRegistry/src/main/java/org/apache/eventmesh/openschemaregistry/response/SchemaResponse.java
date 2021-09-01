package org.apache.eventmesh.openschemaregistry.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SchemaResponse {

    private String id;

    private String name;

    private String comment;

    private String serialization;

    private String schemaType;

    private String schemaDefinition;

    private String validator;

    private int version;

    private String subject;
}

