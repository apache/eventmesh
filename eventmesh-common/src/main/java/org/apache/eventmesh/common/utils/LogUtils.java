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

package org.apache.eventmesh.common.utils;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.spi.CallerBoundaryAware;
import org.slf4j.spi.LoggingEventBuilder;

import lombok.experimental.UtilityClass;

/**
 * This class provides logging methods that encapsulate SLF4J and Supplier.
 * If the log level is not enabled, the passed Supplier is invoked lazily,
 * thereby avoiding unnecessary method execution time.
 * <p>
 * The statement
 * <pre>
 * LogUtil.debug(log, "A time-consuming method: {}", () -> myMethod());
 * </pre>
 * is equivalent to:
 * <pre>
 * if (logger.isDebugEnabled()) {
 *     logger.debug("A time-consuming method: {}", myMethod());
 * }
 * </pre>
 * If no object parameters are passed or existing objects are referenced, use
 * <pre>
 * log.debug("No time-consuming methods: {}", myObject);
 * </pre>
 * instead.
 */

@UtilityClass
public final class LogUtils {

    private static final String FQCN = LogUtils.class.getName();

    public static void debug(Logger logger, String format, Supplier<?> objectSupplier) {
        final LoggingEventBuilder builder = logger.atDebug();
        if (builder instanceof CallerBoundaryAware) {
            ((CallerBoundaryAware) builder).setCallerBoundary(FQCN);
        }
        builder.addArgument(objectSupplier).log(format);
    }

    public static void debug(Logger logger, String format, Supplier<?> objectSupplier, Throwable t) {
        final LoggingEventBuilder builder = logger.atDebug();
        if (builder instanceof CallerBoundaryAware) {
            ((CallerBoundaryAware) builder).setCallerBoundary(FQCN);
        }
        builder.addArgument(objectSupplier).setCause(t).log(format);
    }

    public static void debug(Logger logger, String format, Supplier<?>... objectSuppliers) {
        LoggingEventBuilder builder = logger.atDebug();
        if (builder instanceof CallerBoundaryAware) {
            ((CallerBoundaryAware) builder).setCallerBoundary(FQCN);
        }
        for (Supplier<?> objectSupplier : objectSuppliers) {
            builder = builder.addArgument(objectSupplier);
        }
        builder.log(format);
    }

    public static void info(Logger logger, String format, Supplier<?> objectSupplier) {
        final LoggingEventBuilder builder = logger.atInfo();
        if (builder instanceof CallerBoundaryAware) {
            ((CallerBoundaryAware) builder).setCallerBoundary(FQCN);
        }
        builder.addArgument(objectSupplier).log(format);
    }

    public static void info(Logger logger, String format, Supplier<?> objectSupplier, Throwable t) {
        final LoggingEventBuilder builder = logger.atInfo();
        if (builder instanceof CallerBoundaryAware) {
            ((CallerBoundaryAware) builder).setCallerBoundary(FQCN);
        }
        builder.addArgument(objectSupplier).setCause(t).log(format);
    }

    public static void info(Logger logger, String format, Supplier<?>... objectSuppliers) {
        LoggingEventBuilder builder = logger.atInfo();
        if (builder instanceof CallerBoundaryAware) {
            ((CallerBoundaryAware) builder).setCallerBoundary(FQCN);
        }
        for (Supplier<?> objectSupplier : objectSuppliers) {
            builder = builder.addArgument(objectSupplier);
        }
        builder.log(format);
    }

    public static void warn(Logger logger, String format, Supplier<?> objectSupplier) {
        final LoggingEventBuilder builder = logger.atWarn();
        if (builder instanceof CallerBoundaryAware) {
            ((CallerBoundaryAware) builder).setCallerBoundary(FQCN);
        }
        builder.addArgument(objectSupplier).log(format);
    }

    public static void warn(Logger logger, String format, Supplier<?> objectSupplier, Throwable t) {
        final LoggingEventBuilder builder = logger.atWarn();
        if (builder instanceof CallerBoundaryAware) {
            ((CallerBoundaryAware) builder).setCallerBoundary(FQCN);
        }
        builder.addArgument(objectSupplier).setCause(t).log(format);
    }

    public static void warn(Logger logger, String format, Supplier<?>... objectSuppliers) {
        LoggingEventBuilder builder = logger.atWarn();
        if (builder instanceof CallerBoundaryAware) {
            ((CallerBoundaryAware) builder).setCallerBoundary(FQCN);
        }
        for (Supplier<?> objectSupplier : objectSuppliers) {
            builder = builder.addArgument(objectSupplier);
        }
        builder.log(format);
    }
}
