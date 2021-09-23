/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.eventmesh.openschemaregistry.repository;

import org.apache.eventmesh.openschemaregistry.domain.Schema;
import org.apache.eventmesh.openschemaregistry.domain.SchemaWithSubjectName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SchemaRepository extends JpaRepository<SchemaWithSubjectName, Long> {

    SchemaWithSubjectName getSchemaById(long id);

    @Query(value = "select VERSION from SCHEMA", nativeQuery = true)
    List<Integer> getVersionsBySubject(String subject);

    List<Integer> deleteSchemasBySubject(String subject);

    Integer deleteSchemaBySubjectAndVersion(String subject, int version);

    Schema getSchemaBySubjectAndVersion(String subject, int version);

    List<SchemaWithSubjectName> getSchemaWithSubjectNamesBySubjectOrderByVersionDesc(String subject);
}
