package org.apache.eventmesh.openschemaregistry.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "SUBJECT", schema = "OPENSCHEMA")
public class Subject {
    @Id
    @Column(name = "SUBJECT")
    private String subject;

    @Column(name = "TENANT")
    private String tenant;

    @Column(name = "NAMESPACE")
    private String namespace;

    @Column(name = "APP")
    private String app;

    @Column(name = "DESCRIPTION")
    private String description;

    @Column(name = "STATUS")
    private String status;

    @Column(name = "COMPATIBILITY")
    private String compatibility;

    @Column(name = "COORDINATE")
    private String coordinate;
}
