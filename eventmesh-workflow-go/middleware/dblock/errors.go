package dblock

import "errors"

// ErrGetLockContextCancelled is returned when user given context is cancelled while trying to obtain the lock
var ErrGetLockContextCancelled = errors.New("context cancelled while trying to obtain lock")

// ErrMySQLTimeout is returned when the MySQL server can't acquire the lock in the specified timeout
var ErrMySQLTimeout = errors.New("(mysql) timeout while acquiring the lock")

// ErrMySQLInternalError is returned when MySQL is returning a generic internal error
var ErrMySQLInternalError = errors.New("internal mysql error acquiring the lock")
