package org.apache.eventmesh.openschemaregistry.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "SCHEMA", schema = "OPENSCHEMA")
public class Schema {
    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "NAME")
    private String name;

    @Column(name = "COMMENT")
    private String comment;

    @Column(name = "SERIALIZATION")
    private String serialization;

    @Column(name = "SCHEMATYPE")
    private String schemaType;

    @Column(name = "SCHEMADEFINITION")
    private String schemaDefinition;

    @Column(name = "VALIDATOR")
    private String validator;

    @Column(name = "VERSION")
    private int version;

    @Column(name = "SUBJECT")
    private String subject;
}
