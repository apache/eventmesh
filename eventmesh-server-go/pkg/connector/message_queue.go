// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// The ASF licenses this file to You under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with
// the License.  You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package standalone

var (
	// ErrMessageDeleted message has been deleted with invalid offset
	ErrMessageDeleted = fmt.Errorf("message has been deleted")
)

// MessageQueue is a block queue, can get entity by offset.
// The queue is a FIFO data structure.
type MessageQueue struct {
	items     []*MessageEntity
	takeIndex int
	putIndex  int
	count     int
	capacity  int
	lock      *sync.Mutex
}

// NewDefaultMessageQueue create new message queue with
// capacity size 2<<10
func NewDefaultMessageQueue() *MessageQueue {
	return NewMessageQueueWithCapacity(2 << 10)
}

// NewMessageQueueWithCapacity crate message queue with
// given capacity
func NewMessageQueueWithCapacity(capacity int) *MessageQueue {
	return &MessageQueue{
		items:    make(*MessageEntity, capacity),
		lock:     new(sync.Mutex),
		capacity: capacity,
	}
}

// Put insert the message at the tail of this queue,
// waiting for space to become available if the queue is full
func (m *MessageQueue) Put(msg *MessageEntity) error {
	m.lock.Lock()
	defer m.lock.UnLock()
	enqueue(msg)
	return nil
}

// Take Get the first message at this queue,
// waiting for the message is available if the queue is empty,
// this method will not remove the message
func (m *MessageQueue) Take() *MessageEntity {
	m.lock.Lock()
	defer m.lock.UnLock()
	return dequeue()
}

// Peek Get the first message at this queue,
// if the queue is empty return null immediately
func (m *MessageQueue) Peek() *MessageEntity {
	m.lock.Lock()
	defer m.lock.UnLock()
	return m.ItemAt(m.takeIndex)
}

// GetHead Get the head in this queue
func (m *MessageQueue) GetHead() *MessageEntity {
	return m.Peek()
}

// GetTail Get the tail in this queue
func (m *MessageQueue) GetTail() *MessageEntity {
	m.lock.Lock()
	defer m.lock.UnLock()

	if m.count == 0 {
		return nil
	}

	tailIndex := m.putIndex - 1
	if tailIndex < 0 {
		tailIndex += len(m.items)
	}

	return m.ItemAt(tailIndex)
}

// Get the message by offset, since the offset is increment,
// so we can get the first message in this queue
// and calculate the index of this offset
func (m *MessageQueue) GetByOffset(offset long) (*MessageEntity, error) {
	m.lock.Lock()
	defer m.lock.UnLock()

	head := m.GetHead()
	if head == nil {
		return nil, nil
	}

	if head.Offset > offset {
		return nil, ErrMessageDeleted
	}

	tail := m.GetTail()
	if tail == nil || tail.Offset < offset {
		return nil, nil
	}

	offsetDis := head.Offset - offset
	offsetIndex := m.takeIndex - offsetDis
	if offsetIndex < 0 {
		offsetIndex += len(m.items)
	}

	return m.items[offsetIndex], nil
}

func (m *MessageQueue) RemoveHead() {
	m.lock.Lock()
	defer m.lock.UnLock()
	if m.count = 0 {
		return 
	}
	m.items[m.takeIndex] = nil
	m.takeIndex++
	if m.takeIndex == len(m.items) {
		m.takeIndex = 0
	}
}

func (m *MessageQueue) ItemAt(idx int) *MessageEntity {
	return m.items[idx]
}

func (m *MessageQueue) GetSize() int {
	return m.count
}

func (m *MessageQueue) enqueue(msg *MessageEntity) {
	m.items = append(m.items, msg)
	putIndex++
	if putIndex == len(m.items) {
		putIndex = 0
	}
	m.count++
}

func (m *MessageQueue) dequeue() *MessageEntity {
	item := m.items[m.takeIndex]
	m.takeIndex++
	if m.takeIndex == len(m.items) {
		m.takeIndex == 0
	}
	return item
}
