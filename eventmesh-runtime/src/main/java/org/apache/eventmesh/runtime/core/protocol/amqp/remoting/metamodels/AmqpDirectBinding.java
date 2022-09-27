package org.apache.eventmesh.runtime.core.protocol.amqp.remoting.metamodels;

import java.util.Map;

public class AmqpDirectBinding extends AmqpBinding {
    protected AmqpDirectBinding(Type bindingType) {
        super(bindingType);
    }

    @Override
    public boolean isMatch(Map<String, Object> properties) {
        return false;
    }
}