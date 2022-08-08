package rollwriter

import (
	"bytes"
	"errors"
	"io"
	"time"

	"github.com/hashicorp/go-multierror"
)

// AsyncRollWriter is the asynchronous rolling log writer which implements zapcore.WriteSyncer.
type AsyncRollWriter struct {
	logger io.WriteCloser
	opts   *AsyncOptions

	logQueue chan []byte
	sync     chan struct{}
	syncErr  chan error
	close    chan struct{}
	closeErr chan error
}

// NewAsyncRollWriter create a new AsyncRollWriter.
func NewAsyncRollWriter(logger io.WriteCloser, opt ...AsyncOption) *AsyncRollWriter {
	opts := &AsyncOptions{
		LogQueueSize:     10000,    // default queue size as 10000
		WriteLogSize:     4 * 1024, // default write log size as 4K
		WriteLogInterval: 100,      // default sync interval as 100ms
		DropLog:          false,    // default do not drop logs
	}

	for _, o := range opt {
		o(opts)
	}

	w := &AsyncRollWriter{}
	w.logger = logger
	w.opts = opts
	w.logQueue = make(chan []byte, opts.LogQueueSize)
	w.sync = make(chan struct{})
	w.syncErr = make(chan error)
	w.close = make(chan struct{})
	w.closeErr = make(chan error)

	// start a new goroutine write batch logs.
	go w.batchWriteLog()
	return w
}

// Write writes logs. It implements io.Writer.
func (w *AsyncRollWriter) Write(data []byte) (int, error) {
	log := make([]byte, len(data))
	copy(log, data)
	if w.opts.DropLog {
		select {
		case w.logQueue <- log:
		default:
			return 0, errors.New("log queue is full")
		}
	} else {
		w.logQueue <- log
	}
	return len(data), nil
}

// Sync syncs logs. It implements zapcore.WriteSyncer.
func (w *AsyncRollWriter) Sync() error {
	w.sync <- struct{}{}
	return <-w.syncErr
}

// Close closes current log file. It implements io.Closer.
func (w *AsyncRollWriter) Close() error {
	err := w.Sync()
	close(w.close)
	return multierror.Append(err, <-w.closeErr).ErrorOrNil()
}

// batchWriteLog asynchronously writes logs in batches.
func (w *AsyncRollWriter) batchWriteLog() {
	buffer := bytes.NewBuffer(make([]byte, 0, w.opts.WriteLogSize*2))
	ticker := time.NewTicker(time.Millisecond * time.Duration(w.opts.WriteLogInterval))
	defer ticker.Stop()

	for {
		select {
		case <-ticker.C:
			if buffer.Len() > 0 {
				_, _ = w.logger.Write(buffer.Bytes())
				buffer.Reset()
			}
		case data := <-w.logQueue:
			buffer.Write(data)
			if buffer.Len() >= w.opts.WriteLogSize {
				_, _ = w.logger.Write(buffer.Bytes())
				buffer.Reset()
			}
		case <-w.sync:
			var err error
			if buffer.Len() > 0 {
				_, e := w.logger.Write(buffer.Bytes())
				err = multierror.Append(err, e).ErrorOrNil()
				buffer.Reset()
			}
			size := len(w.logQueue)
			for i := 0; i < size; i++ {
				v := <-w.logQueue
				_, e := w.logger.Write(v)
				err = multierror.Append(err, e).ErrorOrNil()
			}
			w.syncErr <- err
		case <-w.close:
			w.closeErr <- w.logger.Close()
			return
		}
	}
}
