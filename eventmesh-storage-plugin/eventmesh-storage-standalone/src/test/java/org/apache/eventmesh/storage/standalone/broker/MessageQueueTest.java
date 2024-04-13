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

import static org.apache.eventmesh.storage.standalone.TestUtils.createDefaultMessageEntity;

import org.apache.eventmesh.storage.standalone.broker.model.MessageEntity;

import java.util.Arrays;
import java.util.Objects;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MessageQueueTest {

    private static final int DEFAULT_SIZE = 5;
    private static final int ITEMS_COUNT = 1;
    private static final int DEFAULT_OFFSET = 0;
    private static final int WRONG_OFFSET = 4;
    private MessageQueue messageQueue;

    @BeforeEach
    public void setUp() throws InterruptedException {
        initMessageQueue();
    }

    @Test
    public void testPut() {
        Assertions.assertTrue(Arrays.stream(messageQueue.getItems()).findAny().isPresent());
    }

    @Test
    public void testTake() throws InterruptedException {
        MessageEntity takeMessage = messageQueue.take();
        Assertions.assertNotNull(takeMessage);
        Assertions.assertTrue(Arrays.stream(messageQueue.getItems()).findAny().isPresent());
    }

    @Test
    public void testPeek() {
        MessageEntity peekMessage = messageQueue.peek();
        Assertions.assertNotNull(peekMessage);
        Assertions.assertTrue(Arrays.stream(messageQueue.getItems()).findAny().isPresent());
    }

    @Test
    public void testGetHead() {
        MessageEntity headMessage = messageQueue.getHead();
        Assertions.assertNotNull(headMessage);
        Assertions.assertTrue(Arrays.stream(messageQueue.getItems()).findAny().isPresent());
    }

    @Test
    public void testGetTail() {
        MessageEntity tailMessage = messageQueue.getHead();
        Assertions.assertNotNull(tailMessage);
        Assertions.assertTrue(Arrays.stream(messageQueue.getItems()).findAny().isPresent());
    }

    @Test
    public void testGetByOffset() {
        MessageEntity offSetMessageEntity = messageQueue.getByOffset(DEFAULT_OFFSET);
        Assertions.assertNotNull(offSetMessageEntity);
        Assertions.assertTrue(Arrays.stream(messageQueue.getItems()).findAny().isPresent());
        Assertions.assertEquals(DEFAULT_OFFSET, offSetMessageEntity.getOffset());
    }

    @Test
    public void testGetByOffset_whenOffSetIsWrong_thenReturnsNull() {
        MessageEntity offSetMessageEntity = messageQueue.getByOffset(WRONG_OFFSET);
        Assertions.assertNull(offSetMessageEntity);
    }

    @Test
    public void testRemoveHead() {
        messageQueue.removeHead();
        Assertions.assertTrue(Arrays.stream(messageQueue.getItems()).anyMatch(Objects::isNull));
    }

    @Test
    public void testGetSize() {
        Assertions.assertEquals(ITEMS_COUNT, messageQueue.getSize());
    }

    @Test
    public void testGetTakeIndex() throws InterruptedException {
        MessageEntity takeIndexMessageEntity = messageQueue.take();
        Assertions.assertNotNull(takeIndexMessageEntity);
        Assertions.assertEquals(1, messageQueue.getPutIndex());
    }

    @Test
    public void testGetPutIndex() {
        Assertions.assertEquals(1, messageQueue.getPutIndex());
    }

    private void initMessageQueue() throws InterruptedException {
        messageQueue = new MessageQueue(DEFAULT_SIZE);
        MessageEntity messageEntity = createDefaultMessageEntity();
        messageQueue.put(messageEntity);
    }
}
