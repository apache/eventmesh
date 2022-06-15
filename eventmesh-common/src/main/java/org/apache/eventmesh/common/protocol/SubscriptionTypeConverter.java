package org.apache.eventmesh.common.protocol;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.Converter;

public class SubscriptionTypeConverter implements Converter<String, SubscriptionType> {
    @Override
    public SubscriptionType convert(String value) {
        return SubscriptionType.valueOf(value);
    }

    @Override
    public JavaType getInputType(TypeFactory typeFactory) {
        return typeFactory.constructType(String.class);
    }

    @Override
    public JavaType getOutputType(TypeFactory typeFactory) {
        return typeFactory.constructType(SubscriptionType.class);
    }
}
