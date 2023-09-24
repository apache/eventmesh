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

import lombok.experimental.UtilityClass;

@UtilityClass
public final class LogUtils {

    public static boolean isTraceEnabled(Logger logger) {
        return logger.isTraceEnabled();
    }

    public static void trace(Logger logger, String msg) {
        if (isTraceEnabled(logger)) {
            logger.trace(msg);
        }
    }

    public static void trace(Logger logger, String format, Object arg) {
        if (isTraceEnabled(logger)) {
            logger.trace(format, arg);
        }
    }

    public static void trace(Logger logger, String format, Object arg1, Object arg2) {
        if (isTraceEnabled(logger)) {
            logger.trace(format, arg1, arg2);
        }
    }

    public static void trace(Logger logger, String format, Object... arguments) {
        if (isTraceEnabled(logger)) {
            logger.trace(format, arguments);
        }
    }

    public static void trace(Logger logger, String msg, Throwable t) {
        if (isTraceEnabled(logger)) {
            logger.trace(msg, t);
        }
    }

    public static boolean isTraceEnabled(Logger logger, Marker marker) {
        return logger.isTraceEnabled(marker);
    }

    public static void trace(Logger logger, Marker marker, String msg) {
        if (isTraceEnabled(logger, marker)) {
            logger.trace(marker, msg);
        }
    }

    public static void trace(Logger logger, Marker marker, String format, Object arg) {
        if (isTraceEnabled(logger, marker)) {
            logger.trace(marker, format, arg);
        }
    }

    public static boolean isDebugEnabled(Logger logger) {
        return logger.isDebugEnabled();
    }

    public static void debug(Logger logger, String msg) {
        if (isDebugEnabled(logger)) {
            logger.debug(msg);
        }
    }

    public static void debug(Logger logger, String format, Object arg) {
        if (isDebugEnabled(logger)) {
            logger.debug(format, arg);
        }
    }

    public static void debug(Logger logger, String format, Object arg1, Object arg2) {
        if (isDebugEnabled(logger)) {
            logger.debug(format, arg1, arg2);
        }
    }

    public static void debug(Logger logger, String format, Object... arguments) {
        if (isDebugEnabled(logger)) {
            logger.debug(format, arguments);
        }
    }

    public static void debug(Logger logger, String msg, Throwable t) {
        if (isDebugEnabled(logger)) {
            logger.debug(msg, t);
        }
    }

    public static boolean isInfoEnabled(Logger logger) {
        return logger.isInfoEnabled();
    }

    public static void info(Logger logger, String msg) {
        if (isInfoEnabled(logger)) {
            logger.info(msg);
        }
    }

    public static void info(Logger logger, String format, Object arg) {
        if (isInfoEnabled(logger)) {
            logger.info(format, arg);
        }
    }

    public static void info(Logger logger, String format, Object arg1, Object arg2) {
        if (isInfoEnabled(logger)) {
            logger.info(format, arg1, arg2);
        }
    }

    public static void info(Logger logger, String format, Object... arguments) {
        if (isInfoEnabled(logger)) {
            logger.info(format, arguments);
        }
    }

    public static void info(Logger logger, String msg, Throwable t) {
        if (isInfoEnabled(logger)) {
            logger.info(msg, t);
        }
    }

    public static boolean isWarnEnabled(Logger logger) {
        return logger.isWarnEnabled();
    }

    public static void warn(Logger logger, String msg) {
        if (isWarnEnabled(logger)) {
            logger.warn(msg);
        }
    }

    public static void warn(Logger logger, String format, Object arg) {
        if (isWarnEnabled(logger)) {
            logger.warn(format, arg);
        }
    }

    public static void warn(Logger logger, String format, Object arg1, Object arg2) {
        if (isWarnEnabled(logger)) {
            logger.warn(format, arg1, arg2);
        }
    }

    public static void warn(Logger logger, String format, Object... arguments) {
        if (isWarnEnabled(logger)) {
            logger.warn(format, arguments);
        }
    }

    public static void warn(Logger logger, String msg, Throwable t) {
        if (isWarnEnabled(logger)) {
            logger.warn(msg, t);
        }
    }

    public static boolean isErrorEnabled(Logger logger) {
        return logger.isErrorEnabled();
    }

    public static void error(Logger logger, String msg) {
        if (isErrorEnabled(logger)) {
            logger.error(msg);
        }
    }

    public static void error(Logger logger, String format, Object arg) {
        if (isErrorEnabled(logger)) {
            logger.error(format, arg);
        }
    }

    public static void error(Logger logger, String format, Object arg1, Object arg2) {
        if (isErrorEnabled(logger)) {
            logger.error(format, arg1, arg2);
        }
    }

    public static void error(Logger logger, String format, Object... arguments) {
        if (isErrorEnabled(logger)) {
            logger.error(format, arguments);
        }
    }

    public static void error(Logger logger, String msg, Throwable t) {
        if (isErrorEnabled(logger)) {
            logger.error(msg, t);
        }
    }

}
