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

package org.apache.eventmesh.connector.http.util;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class QueueUtils {

    public static final Object lock = new Object();


    /**
     * Add an object to the queue, if the queue is full, remove the head element and add the new element <br/>
     * If you want to add new elements to the
     * queue, you must call this method
     *
     * @param queue    queue
     * @param obj      object to be added
     * @param maxSize  max size of the queue
     * @param currSize current size of the queue
     * @param <T>      object type
     */
    public static <T> void addWithCover(ConcurrentLinkedQueue<T> queue, T obj, int maxSize, AtomicInteger currSize) {
        // If current size is less than max size, add the object to the queue
        if (currSize.get() < maxSize && queue.offer(obj)) {
            currSize.incrementAndGet();
            return;
        }
        // Only lock add operation
        synchronized (lock) {
            // double check
            if (currSize.get() < maxSize && queue.offer(obj)) {
                currSize.incrementAndGet();
                return;
            }
            // If the queue is full, remove the head element and add the new element
            T t = queue.poll();
            queue.offer(obj);
            if (t == null) {
                currSize.incrementAndGet();
            }
        }

    }

}
