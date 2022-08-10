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

package rollwriter

// AsyncOptions is the call options of AsyncRollWriter.
type AsyncOptions struct {
	// LogQueueSize is the queue size of asynchronous log.
	LogQueueSize int

	// WriteLogSize is the threshold to write async log.
	WriteLogSize int

	// WriteLogInterval is the time interval to write async log.
	WriteLogInterval int

	// DropLog determines whether to discard logs when log queue is full.
	DropLog bool
}

// AsyncOption modifies the AsyncOptions.
type AsyncOption func(*AsyncOptions)

// WithLogQueueSize returns an AsyncOption which sets log queue size.
func WithLogQueueSize(n int) AsyncOption {
	return func(o *AsyncOptions) {
		o.LogQueueSize = n
	}
}

// WithWriteLogSize returns an AsyncOption which sets log size(Byte) threshold.
func WithWriteLogSize(n int) AsyncOption {
	return func(o *AsyncOptions) {
		o.WriteLogSize = n
	}
}

// WithWriteLogInterval returns an AsyncOption which sets log interval(ms) threshold(ms).
func WithWriteLogInterval(n int) AsyncOption {
	return func(o *AsyncOptions) {
		o.WriteLogInterval = n
	}
}

// WithDropLog returns an AsyncOption which set whether to drop logs on log queue full.
func WithDropLog(b bool) AsyncOption {
	return func(o *AsyncOptions) {
		o.DropLog = b
	}
}
