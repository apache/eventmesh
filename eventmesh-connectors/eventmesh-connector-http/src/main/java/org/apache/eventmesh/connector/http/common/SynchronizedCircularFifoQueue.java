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

import org.apache.commons.collections4.queue.CircularFifoQueue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;


/**
 * SynchronizedCircularFifoQueue is a synchronized version of CircularFifoQueue.
 */
public class SynchronizedCircularFifoQueue<E> extends CircularFifoQueue<E> {

    /**
     * <p>Default constructor. capacity = 32</p>
     */
    public SynchronizedCircularFifoQueue() {
        super();
    }

    public SynchronizedCircularFifoQueue(Collection<? extends E> coll) {
        super(coll);
    }

    public SynchronizedCircularFifoQueue(int size) {
        super(size);
    }

    @Override
    public synchronized boolean add(E element) {
        return super.add(element);
    }

    @Override
    public synchronized void clear() {
        super.clear();
    }

    @Override
    public synchronized E element() {
        return super.element();
    }

    @Override
    public synchronized E get(int index) {
        return super.get(index);
    }

    @Override
    public synchronized boolean isAtFullCapacity() {
        return super.isAtFullCapacity();
    }

    @Override
    public synchronized boolean isEmpty() {
        return super.isEmpty();
    }

    @Override
    public synchronized boolean isFull() {
        return super.isFull();
    }

    @Override
    public synchronized int maxSize() {
        return super.maxSize();
    }

    @Override
    public synchronized boolean offer(E element) {
        return super.offer(element);
    }

    @Override
    public synchronized E peek() {
        return super.peek();
    }

    @Override
    public synchronized E poll() {
        return super.poll();
    }

    @Override
    public synchronized E remove() {
        return super.remove();
    }

    @Override
    public synchronized int size() {
        return super.size();
    }

    /**
     * <p>Fetch a range of elements from the queue.</p>
     *
     * @param start   start index
     * @param end     end index
     * @param removed whether to remove the elements from the queue
     * @return list of elements
     */
    public synchronized List<E> fetchRange(int start, int end, boolean removed) {

        if (start < 0 || start > end) {
            throw new IllegalArgumentException("Invalid range");
        }
        end = Math.min(end, this.size());

        Iterator<E> iterator = this.iterator();
        List<E> items = new ArrayList<>(end - start);

        int count = 0;
        while (iterator.hasNext() && count < end) {
            E item = iterator.next();
            if (item != null && count >= start) {
                // Add the element to the list
                items.add(item);
                if (removed) {
                    // Remove the element from the queue
                    iterator.remove();
                }
            }
            count++;
        }
        return items;
    }


}
