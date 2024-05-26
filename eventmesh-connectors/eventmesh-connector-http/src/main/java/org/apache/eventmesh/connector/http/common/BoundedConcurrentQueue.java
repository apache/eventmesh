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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * BoundedConcurrentQueue is a wrapper class for ConcurrentLinkedQueue,it has the following features:
 * 1. Limit the maximum size of the queue
 * 2. Add an object to the queue, if the queue is full, remove the head element and add the new element.(thread safe)
 * 3. Poll an object from the queue.(thread safe)
 * 4. Fetch a range of elements from the queue.(thread safe)
 */
public class BoundedConcurrentQueue<T> {

    private final int maxSize;

    private final AtomicInteger currSize;

    private final ConcurrentLinkedQueue<T> queue;

    private final ReadWriteLock globalLock;


    public BoundedConcurrentQueue(int maxSize, boolean isFair) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxQueueSize must be greater than 0");
        }
        this.maxSize = maxSize;
        this.currSize = new AtomicInteger(0);
        this.queue = new ConcurrentLinkedQueue<>();
        this.globalLock = new ReentrantReadWriteLock(isFair);
    }

    public int getMaxSize() {
        return maxSize;
    }

    public int getCurrSize() {
        return currSize.get();
    }

    /**
     * Add an object to the queue, if the queue is full, remove the head element and add the new element.
     *
     * @param obj object to be added
     */
    public void offerWithReplace(T obj) {
        if (obj == null) {
            return;
        }
        // Just adding new elements doesn't affect the iteration in a bad way, so there is no need for locks
        if (currSize.get() < maxSize && queue.offer(obj)) {
            currSize.incrementAndGet();
            return;
        }

        // When the queue is full, only one add operation is allowed
        globalLock.writeLock().lock();
        try {
            // double check
            if (currSize.get() < maxSize && queue.offer(obj)) {
                currSize.incrementAndGet();
                return;
            }
            // remove the head element
            T removedObj = queue.poll();
            // add the new element to tail
            if (!queue.offer(obj)) {
                throw new IllegalStateException("Unable to add element to the queue.");
            } else if (removedObj == null) {
                currSize.incrementAndGet();
            }
        } finally {
            globalLock.writeLock().unlock();
        }

    }


    /**
     * Poll an object from the queue.
     *
     * @return object
     */
    public T poll() {
        globalLock.readLock().lock();
        try {
            T obj = queue.poll();
            if (obj != null) {
                currSize.decrementAndGet();
            }
            return obj;
        } finally {
            globalLock.readLock().unlock();
        }

    }


    /**
     * Fetch a range of elements from the queue.
     *
     * @param start   start index
     * @param end     end index
     * @param removed whether to remove the elements from the queue
     * @return list of elements
     */
    public List<T> fetchRange(int start, int end, boolean removed) {
        if (start < 0 || end > maxSize || start > end) {
            throw new IllegalArgumentException("Invalid range");
        }

        // Ensure that there is no deletion of elements when iterating over them
        globalLock.writeLock().lock();
        try {
            Iterator<T> iterator = queue.iterator();
            List<T> items = new ArrayList<>(end - start);

            int count = 0;
            while (iterator.hasNext() && count < end) {
                T item = iterator.next();
                if (count >= start) {
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
        } finally {
            globalLock.writeLock().unlock();
        }

    }

}
