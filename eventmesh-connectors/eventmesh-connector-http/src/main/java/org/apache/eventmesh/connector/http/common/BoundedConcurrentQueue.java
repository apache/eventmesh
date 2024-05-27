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

package org.apache.eventmesh.connector.http.common;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p>BoundedConcurrentQueue is a wrapper class for ConcurrentLinkedQueue</p>
 * <ol>
 * <li>Limit the maximum size of the queue</li>
 * <li>Add an object to the queue, if the queue is full, remove the head element and add the new element.(thread safe)</li>
 * <li>Poll an object from the queue.(thread safe)</li>
 * <li>Fetch a range of elements from the queue.(weak consistency)</li>
 * </ol>
 */
public class BoundedConcurrentQueue<T> {

    private final int maxSize;

    private final AtomicInteger currSize;

    private final ConcurrentLinkedQueue<T> queue;

    // Lock for add operation
    private final Object addLock = new Object();

    private final AtomicBoolean doReplace = new AtomicBoolean(false);


    public BoundedConcurrentQueue(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxQueueSize must be greater than 0");
        }
        this.maxSize = maxSize;
        this.currSize = new AtomicInteger(0);
        this.queue = new ConcurrentLinkedQueue<>();
    }

    public int getMaxSize() {
        return maxSize;
    }

    public int getCurrSize() {
        return currSize.get();
    }

    /**
     * <p>Add an object to the queue</p>
     * <ul>
     * <li>If the queue is full, remove the head element and add the new element.</li>
     * <li>When the queue capacity is continuously saturated, each add operation generates a lock contention,
     * and means are needed to avoid this approach. e.g. Suitable capacity and equal-rate {@link #poll()}</li>
     * </ul>
     *
     * @param obj object to be added
     */
    public void offerWithReplace(T obj) {
        if (obj == null) {
            return;
        }

        // try to do offer(no-blocking)
        // 1. currSize < maxSize
        // 2. no old values are being deleted(queue is not full)
        // 3. try to add the new element
        if (currSize.get() < maxSize && !doReplace.get() && queue.offer(obj)) {
            currSize.incrementAndGet();
            return;
        }

        // try to do offer again(blocking)
        synchronized (addLock) {
            // double check inside the lock
            if (currSize.get() < maxSize && queue.offer(obj)) {
                currSize.incrementAndGet();
                return;
            }
            // remove the head element and add the new element
            doReplace.set(true);
            try {
                T removedObj = queue.poll();
                if (!queue.offer(obj)) {
                    // abnormal behavior
                    throw new IllegalStateException("Unable to add element to queue");
                } else if (removedObj == null) {
                    // it is equivalent to just adding new element
                    currSize.incrementAndGet();
                }
            } finally {
                // finish replace
                doReplace.set(false);
            }
        }

    }


    /**
     * <p>Poll an object from the queue.</p>
     *
     * @return object
     */
    public T poll() {
        T obj = queue.poll();
        if (obj != null) {
            currSize.decrementAndGet();
        }
        return obj;
    }


    /**
     * <p>Fetch a range of elements from the queue(weakly consistent).</p>
     * <ul>
     * <li> In the case of concurrent modification, the elements may not be fetched as expected.</li>
     * <li>Avoiding simultaneous use with the {@link  #poll()} method can greatly reduce the risk of corresponding</li>
     * </ul>
     *
     * @param start   start index
     * @param end     end index
     * @param removed whether to remove the elements from the queue
     * @return list of elements
     */
    public List<T> fetchRange(int start, int end, boolean removed) {
        if (start < 0 || end > getCurrSize() || start > end) {
            throw new IllegalArgumentException("Invalid range");
        }

        Iterator<T> iterator = queue.iterator();
        List<T> items = new ArrayList<>(end - start);

        int count = 0;
        while (iterator.hasNext() && count < end) {
            T item = iterator.next();
            if (item != null && count >= start) {
                // Add the element to the list
                items.add(item);
                if (removed) {
                    // Remove the element from the queue
                    iterator.remove();
                    currSize.decrementAndGet();
                }
            }
            count++;
        }

        return items;

    }

}
