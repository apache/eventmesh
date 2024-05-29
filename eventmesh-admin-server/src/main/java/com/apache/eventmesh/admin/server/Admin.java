package com.apache.eventmesh.admin.server;

import org.apache.eventmesh.common.remote.Task;
import org.apache.eventmesh.common.remote.request.ReportHeartBeatRequest;
import org.apache.eventmesh.common.utils.PagedList;

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
