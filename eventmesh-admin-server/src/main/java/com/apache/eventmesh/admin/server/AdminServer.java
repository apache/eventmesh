package com.apache.eventmesh.admin.server;

import org.apache.eventmesh.common.utils.PagedList;

import com.apache.eventmesh.admin.server.task.Task;

public class AdminServer implements Admin {
    @Override
    public boolean createOrUpdateTask(Task task) {
        return false;
    }

    @Override
    public boolean deleteTask(Long id) {
        return false;
    }

    @Override
    public Task getTask(Long id) {
        return null;
    }

    @Override
    public PagedList<Task> getTaskPaged(Task task) {
        return null;
    }

    @Override
    public void reportHeartbeat(HeartBeat heartBeat) {

    }

    @Override
    public void start() {

    }

    @Override
    public void destroy() {

    }
}
