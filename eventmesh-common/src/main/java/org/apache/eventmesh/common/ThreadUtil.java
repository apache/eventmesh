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

<<<<<<<< HEAD:eventmesh-common/src/main/java/org/apache/eventmesh/common/utils/ThreadUtils.java
package org.apache.eventmesh.common.utils;

import org.apache.logging.log4j.util.ProcessIdUtil;
========
package org.apache.eventmesh.common;
>>>>>>>> e4cff57da85093ca7a917f7edd86fa434000d5dc:eventmesh-common/src/main/java/org/apache/eventmesh/common/ThreadUtil.java

import java.util.concurrent.ThreadLocalRandom;

public class ThreadUtils {

<<<<<<<< HEAD:eventmesh-common/src/main/java/org/apache/eventmesh/common/utils/ThreadUtils.java
    private static volatile long currentPID = -1;
========
    private static long currentPID = -1;
>>>>>>>> e4cff57da85093ca7a917f7edd86fa434000d5dc:eventmesh-common/src/main/java/org/apache/eventmesh/common/ThreadUtil.java

    public static void randomSleep(int min, int max) throws Exception {
        // nextInt is normally exclusive of the top value, so add 1 to make it inclusive
        int random = ThreadLocalRandom.current().nextInt(min, max + 1);
        Thread.sleep(random);

    }

    public static void randomSleep(int max) throws Exception {
        randomSleep(1, max);
    }

    /**
<<<<<<<< HEAD:eventmesh-common/src/main/java/org/apache/eventmesh/common/utils/ThreadUtils.java
     * get current process id.
     *
     * @return process id
     */
    public static long getPID() {
        if (currentPID == -1) {
            synchronized (ThreadUtils.class) {
                if (currentPID == -1) {
                    currentPID = Long.parseLong(ProcessIdUtil.getProcessId());
                }
========
     * get current process id only once.
     *
     * @return
     */
    public static long getPID() {
        if (currentPID >= 0) {
            return currentPID;
        }
        String processName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
        if (processName != null && processName.length() > 0) {
            try {
                currentPID = Long.parseLong(processName.split("@")[0]);
            } catch (Exception e) {
                return 0;
>>>>>>>> e4cff57da85093ca7a917f7edd86fa434000d5dc:eventmesh-common/src/main/java/org/apache/eventmesh/common/ThreadUtil.java
            }
        }
        return currentPID;
    }
}
