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
