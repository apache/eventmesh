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

package dblock

import (
	"context"
	"database/sql"
	"time"
)

// Lock denotes an acquired lock and presents two methods, one for getting the context which is cancelled when the lock
// is lost/released and other for Releasing the lock
type Lock struct {
	key             string
	conn            *sql.Conn
	unlocker        chan struct{}
	lostLockContext context.Context
	cancelFunc      context.CancelFunc
}

// GetContext returns a context which is cancelled when the lock is lost or released
func (l Lock) GetContext() context.Context {
	return l.lostLockContext
}

// Release unlocks the lock
func (l Lock) Release() error {
	l.unlocker <- struct{}{}
	if _, err := l.conn.ExecContext(context.Background(), "DO RELEASE_LOCK(?)", l.key); err != nil {
		return err
	}
	return l.conn.Close()
}

func (l Lock) refresher(duration time.Duration, cancelFunc context.CancelFunc) {
	for {
		select {
		case <-time.After(duration):
			deadline := time.Now().Add(duration)
			contextDeadline, deadlineCancelFunc := context.WithDeadline(context.Background(), deadline)

			// try refresh, else cancel
			err := l.conn.PingContext(contextDeadline)
			if err != nil {
				cancelFunc()
				deadlineCancelFunc()
				// this will make sure connection is closed
				l.Release()
				return
			}
			deadlineCancelFunc() // to avoid context leak
		case <-l.unlocker:
			cancelFunc()
			return
		}
	}
}
