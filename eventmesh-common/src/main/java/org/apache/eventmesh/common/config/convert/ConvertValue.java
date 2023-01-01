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

package org.apache.eventmesh.common.config.convert;

import java.util.Properties;

/**
 * convert convertInfo to obj
 *
 * @param <T> obj type
 */
public interface ConvertValue<T> {

    T convert(ConvertInfo convertInfo);

    /**
     * @return Whether can to process null values
     */
    default boolean canHandleNullValue() {
        return false;
    }

    /**
     * @return The value converter needs
     */
    default Object processFieldValue(Properties config, String key) {
        return config.getProperty(key);
    }

    class DefaultConverter implements ConvertValue<Object> {

        @Override
        public Object convert(ConvertInfo convertInfo) {
            return null;
        }
    }
}