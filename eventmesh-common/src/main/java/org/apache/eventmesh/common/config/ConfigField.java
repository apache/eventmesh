/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.common.config;

import org.apache.eventmesh.common.config.convert.ConvertValue.DefaultConverter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Record information about the field in the configuration class to be converted
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
public @interface ConfigField {

    /**
     * @return The key name of the configuration file
     */
    String field() default "";

    /**
     * Note : When reload is true, the class must have a reload method
     *
     * @return Whether to reload. This parameter is used when other fields are associated
     */
    boolean reload() default false;

    /**
     * In some special cases, used to specify the converter class of the field
     *
     * @return field converter
     */
    Class<?> converter() default DefaultConverter.class;

    /**
     * if the configuration filed is empty, try to read from env, by field
     *
     * @return Whether to try to read from env if the configuration filed is empty
     */
    boolean findEnv() default false;

    /**
     * If it cannot be null but is null, an exception is thrown
     *
     * @return Whether the field can be null
     */
    boolean notNull() default false;

    /**
     * If it cannot be empty but is empty, an exception is thrown
     *
     * @return Whether the field can be empty
     */
    boolean notEmpty() default false;

    /**
     * If it's not a number, an exception is thrown
     *
     * @return Whether the field must be number
     */
    boolean beNumber() default false;
}
