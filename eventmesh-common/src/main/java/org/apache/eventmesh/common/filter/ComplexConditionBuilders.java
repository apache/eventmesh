package org.apache.eventmesh.common.filter;

import com.google.common.collect.Maps;

import java.lang.reflect.Constructor;
import java.util.Map;

public enum ComplexConditionBuilders {

    prefix(PrefixConditionBuilder.class),

    suffix(SuffixConditionBuilder.class),

    anything_but(AnythingButConditionBuilder.class),

    numeric(NumericConditionBuilder.class),

    cidr(CIDRConditionBuilder.class),

    exists(ExistsConditionBuilder.class);

    private static final Map<String, RuleConditionBuilder> CONDITION_BUILDER = Maps.newConcurrentMap();

    static {
        for (final ComplexConditionBuilders conditionName : ComplexConditionBuilders.values()) {
            CONDITION_BUILDER.put(conditionName.toString(), conditionName.builder());
        }
    }

    public static RuleConditionBuilder getConditionBuilderByName(String name) {
        return CONDITION_BUILDER.get(name);
    }

    private final Class<? extends RuleConditionBuilder> builderClass;

    ComplexConditionBuilders(final Class<? extends RuleConditionBuilder> builderClass) {
        this.builderClass = builderClass;
    }

    RuleConditionBuilder builder() {
        Constructor<? extends RuleConditionBuilder> ctor;
        try {
            ctor = this.builderClass.getDeclaredConstructor();
            return ctor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Couldn't construct the builder for " + builderClass.getSimpleName(), e);
        }
    }

    @Override
    public String toString() {
        return name().replace('_', '-');
    }
}
