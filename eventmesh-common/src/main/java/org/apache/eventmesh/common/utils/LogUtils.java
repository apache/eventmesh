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

import org.slf4j.Logger;
import org.slf4j.Marker;

import com.google.errorprone.annotations.InlineMe;

import lombok.experimental.UtilityClass;

/**
 * This class will be removed in the next major release.
 * Use the {@link Logger} class directly.
 */
@Deprecated
@UtilityClass
public final class LogUtils {

    @InlineMe(replacement = "logger.isTraceEnabled()")
    public static boolean isTraceEnabled(Logger logger) {
        return logger.isTraceEnabled();
    }

    @InlineMe(replacement = "logger.isTraceEnabled(marker)")
    public static boolean isTraceEnabled(Logger logger, Marker marker) {
        return logger.isTraceEnabled(marker);
    }

    @InlineMe(replacement = "logger.trace(msg)")
    public static void trace(Logger logger, String msg) {
        if (logger.isTraceEnabled()) {
            logger.trace(msg);
        }
    }

    @InlineMe(replacement = "logger.trace(format, arg)")
    public static void trace(Logger logger, String format, Object arg) {
        if (logger.isTraceEnabled()) {
            logger.trace(format, arg);
        }
    }

    @InlineMe(replacement = "logger.trace(msg, t)")
    public static void trace(Logger logger, String msg, Throwable t) {
        if (logger.isTraceEnabled()) {
            logger.trace(msg, t);
        }
    }

    @InlineMe(replacement = "logger.trace(format, arguments)")
    public static void trace(Logger logger, String format, Object... arguments) {
        if (logger.isTraceEnabled()) {
            logger.trace(format, arguments);
        }
    }

    @InlineMe(replacement = "logger.trace(format, arg1, arg2)")
    public static void trace(Logger logger, String format, Object arg1, Object arg2) {
        if (logger.isTraceEnabled()) {
            logger.trace(format, arg1, arg2);
        }
    }

    @InlineMe(replacement = "logger.trace(marker, msg)")
    public static void trace(Logger logger, Marker marker, String msg) {
        if (logger.isTraceEnabled(marker)) {
            logger.trace(marker, msg);
        }
    }

    @InlineMe(replacement = "logger.trace(marker, format, arg)")
    public static void trace(Logger logger, Marker marker, String format, Object arg) {
        if (logger.isTraceEnabled(marker)) {
            logger.trace(marker, format, arg);
        }
    }

    @InlineMe(replacement = "logger.isDebugEnabled()")
    public static boolean isDebugEnabled(Logger logger) {
        return logger.isDebugEnabled();
    }

    @InlineMe(replacement = "logger.debug(msg)")
    public static void debug(Logger logger, String msg) {
        if (logger.isDebugEnabled()) {
            logger.debug(msg);
        }
    }

    @InlineMe(replacement = "logger.debug(format, arg)")
    public static void debug(Logger logger, String format, Object arg) {
        if (logger.isDebugEnabled()) {
            logger.debug(format, arg);
        }
    }

    @InlineMe(replacement = "logger.debug(format, arg1, arg2)")
    public static void debug(Logger logger, String format, Object arg1, Object arg2) {
        if (logger.isDebugEnabled()) {
            logger.debug(format, arg1, arg2);
        }
    }

    @InlineMe(replacement = "logger.debug(format, arguments)")
    public static void debug(Logger logger, String format, Object... arguments) {
        if (logger.isDebugEnabled()) {
            logger.debug(format, arguments);
        }
    }

    @InlineMe(replacement = "logger.debug(msg, t)")
    public static void debug(Logger logger, String msg, Throwable t) {
        if (logger.isDebugEnabled()) {
            logger.debug(msg, t);
        }
    }

    @InlineMe(replacement = "logger.isInfoEnabled()")
    public static boolean isInfoEnabled(Logger logger) {
        return logger.isInfoEnabled();
    }

    @InlineMe(replacement = "logger.info(msg)")
    public static void info(Logger logger, String msg) {
        if (logger.isInfoEnabled()) {
            logger.info(msg);
        }
    }

    @InlineMe(replacement = "logger.info(format, arg)")
    public static void info(Logger logger, String format, Object arg) {
        if (logger.isInfoEnabled()) {
            logger.info(format, arg);
        }
    }

    @InlineMe(replacement = "logger.info(format, arg1, arg2)")
    public static void info(Logger logger, String format, Object arg1, Object arg2) {
        if (logger.isInfoEnabled()) {
            logger.info(format, arg1, arg2);
        }
    }

    @InlineMe(replacement = "logger.info(format, arguments)")
    public static void info(Logger logger, String format, Object... arguments) {
        if (logger.isInfoEnabled()) {
            logger.info(format, arguments);
        }
    }

    @InlineMe(replacement = "logger.info(msg, t)")
    public static void info(Logger logger, String msg, Throwable t) {
        if (logger.isInfoEnabled()) {
            logger.info(msg, t);
        }
    }

    @InlineMe(replacement = "logger.isWarnEnabled()")
    public static boolean isWarnEnabled(Logger logger) {
        return logger.isWarnEnabled();
    }

    @InlineMe(replacement = "logger.warn(msg)")
    public static void warn(Logger logger, String msg) {
        if (logger.isWarnEnabled()) {
            logger.warn(msg);
        }
    }

    @InlineMe(replacement = "logger.warn(format, arg)")
    public static void warn(Logger logger, String format, Object arg) {
        if (logger.isWarnEnabled()) {
            logger.warn(format, arg);
        }
    }

    @InlineMe(replacement = "logger.warn(format, arg1, arg2)")
    public static void warn(Logger logger, String format, Object arg1, Object arg2) {
        if (logger.isWarnEnabled()) {
            logger.warn(format, arg1, arg2);
        }
    }

    @InlineMe(replacement = "logger.warn(format, arguments)")
    public static void warn(Logger logger, String format, Object... arguments) {
        if (logger.isWarnEnabled()) {
            logger.warn(format, arguments);
        }
    }

    @InlineMe(replacement = "logger.warn(msg, t)")
    public static void warn(Logger logger, String msg, Throwable t) {
        if (logger.isWarnEnabled()) {
            logger.warn(msg, t);
        }
    }

    @InlineMe(replacement = "logger.isErrorEnabled()")
    public static boolean isErrorEnabled(Logger logger) {
        return logger.isErrorEnabled();
    }

    @InlineMe(replacement = "logger.error(msg)")
    public static void error(Logger logger, String msg) {
        if (logger.isErrorEnabled()) {
            logger.error(msg);
        }
    }

    @InlineMe(replacement = "logger.error(format, arg)")
    public static void error(Logger logger, String format, Object arg) {
        if (logger.isErrorEnabled()) {
            logger.error(format, arg);
        }
    }

    @InlineMe(replacement = "logger.error(format, arg1, arg2)")
    public static void error(Logger logger, String format, Object arg1, Object arg2) {
        if (logger.isErrorEnabled()) {
            logger.error(format, arg1, arg2);
        }
    }

    @InlineMe(replacement = "logger.error(format, arguments)")
    public static void error(Logger logger, String format, Object... arguments) {
        if (logger.isErrorEnabled()) {
            logger.error(format, arguments);
        }
    }

    @InlineMe(replacement = "logger.error(msg, t)")
    public static void error(Logger logger, String msg, Throwable t) {
        if (logger.isErrorEnabled()) {
            logger.error(msg, t);
        }
    }

}
