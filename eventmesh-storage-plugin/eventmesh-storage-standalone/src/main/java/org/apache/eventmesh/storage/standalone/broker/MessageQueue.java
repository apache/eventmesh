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

package org.apache.eventmesh.storage.standalone.broker;

import org.apache.eventmesh.storage.standalone.broker.model.MessageEntity;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.google.common.base.Preconditions;

import lombok.Getter;

/**
 * This is a block queue, can get entity by offset. The queue is a FIFO data structure.
 */
public class MessageQueue {

    @Getter
    private final MessageEntity[] items;

    private volatile int takeIndex;

    private volatile int putIndex;

    private volatile int count;

    private final ReentrantLock lock;

    private final Condition notEmpty;

    private final Condition notFull;

    public MessageQueue() {
        this(2 << 10);
    }

    public MessageQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity is illegal");
        }
        this.items = new MessageEntity[capacity];
        this.lock = new ReentrantLock();
        this.notEmpty = lock.newCondition();
        this.notFull = lock.newCondition();
    }

    /**
     * Inserts the specified MessageEntity object into the queue.
     *
     * @param messageEntity The MessageEntity object to be inserted into the queue.
     * @throws InterruptedException if the current thread is interrupted while waiting for space to become available in the queue.
     */
    public void put(MessageEntity messageEntity) throws InterruptedException {
        Preconditions.checkNotNull(messageEntity);
        ReentrantLock reentrantLock = this.lock;
        reentrantLock.lockInterruptibly();
        try {
            while (count == items.length) {
                notFull.await();
            }
            enqueue(messageEntity);
        } finally {
            reentrantLock.unlock();
        }
    }

    /**
     * Get the first message at this queue, waiting for the message is available if the queue is empty, this method will not remove the message
     *
     * @return The MessageEntity object at the head of the queue.
     * @throws InterruptedException if the current thread is interrupted while waiting for an element to become available in the queue.
     */
    public MessageEntity take() throws InterruptedException {
        ReentrantLock reentrantLock = this.lock;
        reentrantLock.lockInterruptibly();
        try {
            while (count == 0) {
                notEmpty.await();
            }
            return dequeue();
        } finally {
            reentrantLock.unlock();
        }
    }

    /**
     * Get the first message at this queue, if the queue is empty return null immediately
     *
     * @return MessageEntity
     */
    public MessageEntity peek() {
        ReentrantLock reentrantLock = this.lock;
        reentrantLock.lock();
        try {
            return itemAt(takeIndex);
        } finally {
            reentrantLock.unlock();
        }
    }

    /**
     * Get the head in this queue
     *
     * @return MessageEntity
     */
    public MessageEntity getHead() {
        return peek();
    }

    /**
     * Get the tail in this queue
     *
     * @return MessageEntity
     */
    public MessageEntity getTail() {
        ReentrantLock reentrantLock = this.lock;
        reentrantLock.lock();
        try {
            if (count == 0) {
                return null;
            }
            int tailIndex = putIndex - 1;
            if (tailIndex < 0) {
                tailIndex += items.length;
            }
            return itemAt(tailIndex);
        } finally {
            reentrantLock.unlock();
        }
    }

    /**
     * Get the message by offset, since the offset is increment, so we can get the first message in this queue and calculate the index of this offset
     *
     * @param offset The offset of the MessageEntity object to be retrieved.
     * @return The MessageEntity object with the specified offset, or null if no such object exists in the queue.
     * @throws RuntimeException if the specified offset is less than the offset of the head MessageEntity object.
     */
    public MessageEntity getByOffset(long offset) {
        ReentrantLock reentrantLock = this.lock;
        reentrantLock.lock();
        try {
            if (count == 0) {
                return null;
            }
            int tailIndex = putIndex - 1;
            MessageEntity head = itemAt(takeIndex);
            if (head.getOffset() > offset) {
                throw new RuntimeException(String.format("The message has been deleted, offset: %s", offset));
            }
            if (tailIndex < 0) {
                tailIndex += items.length;
            }
            MessageEntity tail = itemAt(tailIndex);
            if (tail == null || tail.getOffset() < offset) {
                return null;
            }
            int offsetDis = (int) (head.getOffset() - offset);
            int offsetIndex = takeIndex - offsetDis;
            if (offsetIndex < 0) {
                offsetIndex += items.length;
            }
            return itemAt(offsetIndex);
        } finally {
            reentrantLock.unlock();
        }
    }

    /**
     * Removes the MessageEntity object at the head of the queue.
     */
    public void removeHead() {
        ReentrantLock reentrantLock = this.lock;
        reentrantLock.lock();
        try {
            if (count == 0) {
                return;
            }
            items[takeIndex++] = null;
            if (takeIndex == items.length) {
                takeIndex = 0;
            }
            notFull.signalAll();
        } finally {
            reentrantLock.unlock();
        }
    }

    public int getSize() {
        return count;
    }

    /**
     * Returns the MessageEntity object at the specified index.
     *
     * @param index The index of the MessageEntity object to be returned.
     * @return The MessageEntity object at the specified index.
     */
    private MessageEntity itemAt(int index) {
        return items[index];
    }

    /**
     * Insert the message at the tail of this queue, waiting for space to become available if the queue is full
     *
     * @param messageEntity The MessageEntity object to be inserted into the queue.
     */
    private void enqueue(MessageEntity messageEntity) {
        items[putIndex++] = messageEntity;
        if (putIndex == items.length) {
            putIndex = 0;
        }
        count++;
        notEmpty.signalAll();
    }

    /**
     * Removes and returns the MessageEntity object at the head of the queue.
     *
     * @return The MessageEntity object at the head of the queue.
     */
    private MessageEntity dequeue() {
        MessageEntity item = items[takeIndex++];
        if (takeIndex == items.length) {
            takeIndex = 0;
        }
        count--;
        notFull.signalAll();
        return item;
    }

    public int getTakeIndex() {
        return takeIndex;
    }

    public int getPutIndex() {
        return putIndex;
    }
}
