package org.apache.eventmesh.api;

import io.openmessaging.api.AsyncConsumeContext;

public abstract class MeshAsyncConsumeContext extends AsyncConsumeContext {
    private AbstractContext context;

    public AbstractContext getContext() {
        return context;
    }

    public void setContext(AbstractContext context) {
        this.context = context;
    }
}
