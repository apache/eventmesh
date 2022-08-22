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

package util

import (
	"context"
	"time"
)

// Condition the condition to exit the loop
type Condition func() bool

// defaultInternal internal to check the condition
var defaultInternal = time.Second

// Until wait for the condition is true
type Until struct {
	Cond     Condition
	internal time.Duration
}

// NewUntil create a new until instance
func NewUntil(cond Condition, internal time.Duration) *Until {
	if internal == time.Duration(0) {
		internal = defaultInternal
	}
	return &Until{
		Cond:     cond,
		internal: internal,
	}
}

// Wait loop until the condition is true
func (u *Until) Wait(ctx context.Context) {
	tick := time.NewTicker(u.internal)
	for {
		select {
		case <-ctx.Done():
			return
		case <-tick.C:
			if u.Cond() {
				return
			}
		}
	}
}
