package org.apache.eventmesh.runtime.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface EventMeshTrace {
    /**
     * If true, enable the trace
     */
    boolean isEnable() default false;
}
