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

package org.apache.eventmesh.common.remote.offset.jdbc;

import org.apache.eventmesh.common.remote.offset.RecordPartition;

import java.util.Objects;

import lombok.Data;
import lombok.ToString;


@Data
@ToString
public class JdbcRecordPartition extends RecordPartition {

    private String fileName;

    @Override
    public Class<? extends RecordPartition> getRecordPartitionClass() {
        return JdbcRecordPartition.class;
    }

    public JdbcRecordPartition() {
        super();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JdbcRecordPartition that = (JdbcRecordPartition) o;
        return Objects.equals(fileName, that.fileName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileName);
    }
}
