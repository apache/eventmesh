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

package org.apache.eventmesh.admin.server;

import org.apache.eventmesh.common.ComponentLifeCycle;
import org.apache.eventmesh.common.remote.Task;
import org.apache.eventmesh.common.remote.request.ReportHeartBeatRequest;
import org.apache.eventmesh.common.utils.PagedList;

/**
 * Admin
 */
public interface Admin extends ComponentLifeCycle {

    /**
     * support for web or ops
     **/
    boolean createOrUpdateTask(Task task);

    boolean deleteTask(Long id);

    Task getTask(Long id);

    // paged list
    PagedList<Task> getTaskPaged(Task task);

    /**
     * support for task
     */
    void reportHeartbeat(ReportHeartBeatRequest heartBeat);
}
