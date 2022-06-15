package org.apache.eventmesh.common.protocol;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.Converter;

public class SubscriptionModeConverter implements Converter<String, SubscriptionMode> {
    @Override
    public SubscriptionMode convert(String value) {
        return SubscriptionMode.valueOf(value);
    }

    @Override
    public JavaType getInputType(TypeFactory typeFactory) {
        return typeFactory.constructType(String.class);
    }

    @Override
    public JavaType getOutputType(TypeFactory typeFactory) {
        return typeFactory.constructType(SubscriptionMode.class);
    }
}
