package org.apache.eventmesh.common.filter;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;

import java.util.Map;

public enum NumericOperators {
    /**
     * Check two numbers whether is equal
     */
    EQUAL("="),
    /**
     * Check two numbers whether is not equal
     */
    NOT_EQUAL("!="),
    /**
     * Check two numbers which is greater
     */
    GREATER_THAN(">"),
    /**
     * Check two numbers whether is equal equal
     */
    GREATER_THAN_EQUAL(">="),
    /**
     * Check two numbers whether is less
     */
    LESS_THAN("<"),
    /**
     * Check two numbers whether is less equal
     */
    LESS_THAN_EQUAL("<=");

    private static final double THRESHOLD = 1.0E-7;

    private static final Map<String, NumericOperators> OPERATOR_MAP = Maps.newConcurrentMap();

    static {
        for (final NumericOperators operator : NumericOperators.values()) {
            OPERATOR_MAP.put(operator.toString(), operator);
        }
    }

    public static NumericOperators getOperatorByExp(String exp) {
        if (Strings.isNullOrEmpty(exp)) {
            return null;
        }
        return OPERATOR_MAP.get(exp);
    }

    private final String exp;

    NumericOperators(final String exp) {
        this.exp = exp;
    }

    public boolean match(double src, double dst) {
        // dst is between -1.0E9 and 1.0E9, inclusive
        final int result = compareDouble(src, dst);
        switch (this) {
            case EQUAL:
                return result == 0;
            case NOT_EQUAL:
                return result != 0;
            case GREATER_THAN:
                return result > 0;
            case GREATER_THAN_EQUAL:
                return result >= 0;
            case LESS_THAN:
                return result < 0;
            case LESS_THAN_EQUAL:
                return result <= 0;
            default: // Never be here
                return false;
        }
    }

    private int compareDouble(double src, double dst) {
        if (Math.abs(src - dst) <= THRESHOLD) {
            return 0;
        }
        return Double.compare(src, dst);
    }

    @Override
    public String toString() {
        return exp;
    }
}
