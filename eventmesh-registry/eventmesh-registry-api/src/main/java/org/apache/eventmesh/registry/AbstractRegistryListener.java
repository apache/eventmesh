package org.apache.eventmesh.registry;

public abstract class AbstractRegistryListener<T> implements RegistryListener {
    protected abstract boolean checkType(Object data);
    @Override
    @SuppressWarnings("unchecked")
    public void onChange(Object data) {
        if (!checkType(data)) {
            return;
        }
        process((T)data);
    }
    protected abstract void process(T data);
}
