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

import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.spi.LoggingEventBuilder;

@ExtendWith(MockitoExtension.class)
class LogUtilTest {

    private Logger mockLogger;
    private LoggingEventBuilder mockEventBuilder;
    private Supplier<String> supplier;
    private String logMessage;

    @BeforeEach
    void setUp() {

        mockLogger = mock(Logger.class);
        mockEventBuilder = mock(LoggingEventBuilder.class);

        supplier = () -> "{\"orderId\": 12345, \"amount\": 100}";
        logMessage = "Processing order with data: {}";
    }

    @Test
    void testDebugLogsWithSupplier() {

        doReturn(mockEventBuilder).when(mockLogger).atDebug();
        doReturn(mockEventBuilder).when(mockEventBuilder).addArgument(same(supplier));

        LogUtil.debug(mockLogger, logMessage, supplier);

        verify(mockLogger).atDebug();
        verify(mockEventBuilder).addArgument(same(supplier));
        verify(mockEventBuilder).log(logMessage);

    }

    @Test
    void testDebugLogsWithSupplierAndException() {
        Throwable throwable = new RuntimeException("Order processing failed");


        doReturn(mockEventBuilder).when(mockLogger).atDebug();
        doReturn(mockEventBuilder).when(mockEventBuilder).addArgument(same(supplier));
        doReturn(mockEventBuilder).when(mockEventBuilder).setCause(throwable);

        LogUtil.debug(mockLogger, logMessage, supplier, throwable);

        verify(mockLogger).atDebug();
        verify(mockEventBuilder).addArgument(same(supplier));
        verify(mockEventBuilder).setCause(throwable);
        verify(mockEventBuilder).log(logMessage);
    }

    @Test
    void testDebugLogsWithSuppliers() {

        Supplier<String> supplier2 = () -> "{\"orderId\": 67890, \"amount\": 200}";

        doReturn(mockEventBuilder).when(mockLogger).atDebug();
        doReturn(mockEventBuilder).when(mockEventBuilder).addArgument(same(supplier));
        doReturn(mockEventBuilder).when(mockEventBuilder).addArgument(same(supplier2));

        LogUtil.debug(mockLogger, logMessage, supplier, supplier2);

        verify(mockLogger).atDebug();
        verify(mockEventBuilder).addArgument(same(supplier));
        verify(mockEventBuilder).addArgument(same(supplier2));
        verify(mockEventBuilder).log(logMessage);
    }

    @Test
    void testInfoLogsWithSupplier() {

        doReturn(mockEventBuilder).when(mockLogger).atInfo();
        doReturn(mockEventBuilder).when(mockEventBuilder).addArgument(same(supplier));

        LogUtil.info(mockLogger, logMessage, supplier);

        verify(mockLogger).atInfo();
        verify(mockEventBuilder).addArgument(same(supplier));
        verify(mockEventBuilder).log(logMessage);

    }

    @Test
    void testInfoLogsWithSupplierAndException() {

        Throwable throwable = new RuntimeException("Order processing failed");

        doReturn(mockEventBuilder).when(mockLogger).atInfo();
        doReturn(mockEventBuilder).when(mockEventBuilder).addArgument(same(supplier));
        doReturn(mockEventBuilder).when(mockEventBuilder).setCause(throwable);

        LogUtil.info(mockLogger, logMessage, supplier, throwable);

        verify(mockLogger).atInfo();
        verify(mockEventBuilder).addArgument(same(supplier));
        verify(mockEventBuilder).setCause(throwable);
        verify(mockEventBuilder).log(logMessage);

    }

    @Test
    void testInfoLogsWithSuppliers() {

        Supplier<String> supplier2 = () -> "{\"orderId\": 67890, \"amount\": 200}";

        doReturn(mockEventBuilder).when(mockLogger).atInfo();
        doReturn(mockEventBuilder).when(mockEventBuilder).addArgument(same(supplier));
        doReturn(mockEventBuilder).when(mockEventBuilder).addArgument(same(supplier2));

        LogUtil.info(mockLogger, logMessage, supplier, supplier2);

        verify(mockLogger).atInfo();
        verify(mockEventBuilder).addArgument(same(supplier));
        verify(mockEventBuilder).addArgument(same(supplier2));
        verify(mockEventBuilder).log(logMessage);
    }

    @Test
    void testWarnLogsWithSupplier() {

        doReturn(mockEventBuilder).when(mockLogger).atWarn();
        doReturn(mockEventBuilder).when(mockEventBuilder).addArgument(same(supplier));

        LogUtil.warn(mockLogger, logMessage, supplier);

        verify(mockLogger).atWarn();
        verify(mockEventBuilder).addArgument(same(supplier));
        verify(mockEventBuilder).log(logMessage);

    }

    @Test
    void testWarnLogsWithSupplierAndException() {

        Throwable throwable = new RuntimeException("Order processing failed");

        doReturn(mockEventBuilder).when(mockLogger).atWarn();
        doReturn(mockEventBuilder).when(mockEventBuilder).addArgument(same(supplier));
        doReturn(mockEventBuilder).when(mockEventBuilder).setCause(throwable);

        LogUtil.warn(mockLogger, logMessage, supplier, throwable);

        verify(mockLogger).atWarn();
        verify(mockEventBuilder).addArgument(same(supplier));
        verify(mockEventBuilder).setCause(throwable);
        verify(mockEventBuilder).log(logMessage);

    }

    @Test
    void testWarnLogsWithSuppliers() {

        Supplier<String> supplier2 = () -> "{\"orderId\": 67890, \"amount\": 200}";

        doReturn(mockEventBuilder).when(mockLogger).atWarn();
        doReturn(mockEventBuilder).when(mockEventBuilder).addArgument(same(supplier));
        doReturn(mockEventBuilder).when(mockEventBuilder).addArgument(same(supplier2));

        LogUtil.warn(mockLogger, logMessage, supplier, supplier2);

        verify(mockLogger).atWarn();
        verify(mockEventBuilder).addArgument(same(supplier));
        verify(mockEventBuilder).addArgument(same(supplier2));
        verify(mockEventBuilder).log(logMessage);
    }

}