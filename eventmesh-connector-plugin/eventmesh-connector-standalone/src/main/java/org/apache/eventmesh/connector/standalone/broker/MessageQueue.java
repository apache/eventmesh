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

package org.apache.eventmesh.connector.standalone.broker;

import com.google.common.base.Preconditions;
import org.apache.eventmesh.connector.standalone.broker.model.MessageEntity;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This is a block queue, can get entity by offset.
 * The queue is a FIFO data structure.
 */
public class MessageQueue {

    public MessageEntity[] items;

    private int takeIndex;

    private int putIndex;

    private int count;

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
     * Insert the message at the tail of this queue, waiting for space to become available if the queue is full
     *
     * @param messageEntity
     */
    public void put(MessageEntity messageEntity) throws InterruptedException {
        Preconditions.checkNotNull(messageEntity);
        ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            while (count == items.length) {
                notFull.await();
            }
            enqueue(messageEntity);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Get the first message at this queue, waiting for the message is available if the queue is empty,
     * this method will not remove the message
     *
     * @return MessageEntity
     * @throws InterruptedException
     */
    public MessageEntity take() throws InterruptedException {
        ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            while (count == 0) {
                notEmpty.await();
            }
            return dequeue();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Get the first message at this queue, if the queue is empty return null immediately
     *
     * @return MessageEntity
     */
    public MessageEntity peek() {
        ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return itemAt(takeIndex);
        } finally {
            lock.unlock();
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
        ReentrantLock lock = this.lock;
        lock.lock();
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
            lock.unlock();
        }
    }

    /**
     * Get the message by offset, since the offset is increment, so we can get the first message in this queue
     * and calculate the index of this offset
     *
     * @param offset
     * @return MessageEntity
     */
    public MessageEntity getByOffset(long offset) {
        ReentrantLock lock = this.lock;
        lock.lock();
        try {
            MessageEntity head = getHead();
            if (head == null) {
                return null;
            }
            if (head.getOffset() > offset) {
                throw new RuntimeException(String.format("The message has been deleted, offset: %s", offset));
            }
            MessageEntity tail = getTail();
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
            lock.unlock();
        }
    }

    public void removeHead() {
        ReentrantLock lock = this.lock;
        lock.lock();
        try {
            if (count == 0) {
                return;
            }
            items[takeIndex++] = null;
            if (takeIndex == items.length) {
                takeIndex = 0;
            }
            notFull.signal();
        } finally {
            lock.unlock();
        }
    }

    public int getSize() {
        return count;
    }


    private MessageEntity itemAt(int index) {
        return items[index];
    }

    private void enqueue(MessageEntity messageEntity) {
        items[putIndex++] = messageEntity;
        if (putIndex == items.length) {
            putIndex = 0;
        }
        count++;
        notEmpty.signal();
    }

    private MessageEntity dequeue() {
        MessageEntity item = items[takeIndex++];
        if (takeIndex == items.length) {
            takeIndex = 0;
        }
        notFull.signal();
        return item;
    }

}