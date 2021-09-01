package org.apache.eventmesh.openschemaregistry.repository;

import org.apache.eventmesh.openschemaregistry.domain.Schema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SchemaRepository extends JpaRepository<Schema, Long> {

    Schema getSchemaById(long id);
}
