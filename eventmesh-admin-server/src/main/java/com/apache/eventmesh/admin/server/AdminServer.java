package com.apache.eventmesh.admin.server;

import com.apache.eventmesh.admin.server.registry.EventMeshAdminServerRegisterInfo;
import com.apache.eventmesh.admin.server.registry.RegistryService;
import com.apache.eventmesh.admin.server.task.Task;
import org.apache.eventmesh.common.utils.PagedList;

public class AdminServer implements Admin {

    private RegistryService registryService;

    private EventMeshAdminServerRegisterInfo registerInfo;

    public AdminServer(RegistryService registryService, EventMeshAdminServerRegisterInfo registerInfo) {
        this.registryService = registryService;
        this.registerInfo = registerInfo;
    }

    public static final String ConfigurationKey = "admin-server";
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

        registryService.register(registerInfo);
    }

    @Override
    public void destroy() {
        registryService.unRegister(registerInfo);
        registryService.shutdown();
    }
}
