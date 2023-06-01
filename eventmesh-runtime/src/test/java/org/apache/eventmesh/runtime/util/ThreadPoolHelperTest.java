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

package org.apache.eventmesh.runtime.util;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ThreadPoolHelperTest {

    @Mock
    private ThreadPoolExecutor mockThreadPool;

    @Mock
    private RejectedExecutionHandler mockRejectionPolicy;

    @Test
    public void testPrintState() {
        when(mockThreadPool.isShutdown()).thenReturn(false);
        when(mockThreadPool.isTerminating()).thenReturn(false);
        when(mockThreadPool.isTerminated()).thenReturn(false);
        when(mockThreadPool.getActiveCount()).thenReturn(2);
        when(mockThreadPool.getCompletedTaskCount()).thenReturn(10L);
        when(mockThreadPool.getTaskCount()).thenReturn(15L);
        when(mockThreadPool.getQueue()).thenReturn(new LinkedBlockingDeque<>(5));
        when(mockThreadPool.getCorePoolSize()).thenReturn(5);
        when(mockThreadPool.getMaximumPoolSize()).thenReturn(10);
        when(mockThreadPool.getKeepAliveTime(any(TimeUnit.class))).thenReturn(1000L);
        when(mockThreadPool.getRejectedExecutionHandler()).thenReturn(mockRejectionPolicy);

        ThreadPoolHelper.printState(mockThreadPool);

        verify(mockThreadPool).isShutdown();
        verify(mockThreadPool).isTerminating();
        verify(mockThreadPool).isTerminated();
        verify(mockThreadPool).getActiveCount();
        verify(mockThreadPool).getCompletedTaskCount();
        verify(mockThreadPool).getTaskCount();
        verify(mockThreadPool).getQueue();
        verify(mockThreadPool).getCorePoolSize();
        verify(mockThreadPool).getMaximumPoolSize();
        verify(mockThreadPool).getKeepAliveTime(any(TimeUnit.class));
        verify(mockThreadPool).getRejectedExecutionHandler();
    }
}