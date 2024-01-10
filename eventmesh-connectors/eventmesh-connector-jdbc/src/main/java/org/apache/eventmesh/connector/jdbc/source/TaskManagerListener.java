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

package org.apache.eventmesh.connector.jdbc.source;

import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.util.List;

/**
 * The TaskManagerListener is a functional interface used to listen for events from the TaskManager. It defines a single method, "listen", that takes
 * a list of ConnectRecord objects as its parameter.
 */
@FunctionalInterface
public interface TaskManagerListener {

    /**
     * Listens for events from the TaskManager and processes the given list of ConnectRecords.
     *
     * @param records The list of ConnectRecord objects to be processed.
     */
    void listen(List<ConnectRecord> records);

}
