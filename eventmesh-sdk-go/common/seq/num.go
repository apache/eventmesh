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

package seq

import (
	"fmt"
	"go.uber.org/atomic"
)

// Interface to generate sequence number
type Interface interface {
	Next() string
}

// AtomicSeq use atomic.Int64 to create seq number
type AtomicSeq struct {
	*atomic.Uint64
}

// NewAtomicSeq new atomic sequence instance
func NewAtomicSeq() Interface {
	return &AtomicSeq{
		Uint64: atomic.NewUint64(0),
	}
}

func (a *AtomicSeq) Next() string {
	return fmt.Sprintf("%v", a.Inc())
}
