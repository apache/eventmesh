package util

import (
	"runtime"
	"sync"

	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
)

// PanicBufLen is len of buffer used for stack trace logging
// when the goroutine panics, 1024 by default.
var PanicBufLen = 1024

// GoAndWait provides safe concurrent handling. Per input handler, it starts a goroutine.
// Then it waits until all handlers are done and will recover if any handler panics.
// The returned error is the first non-nil error returned by one of the handlers.
// It can be set that non-nil error will be returned if the "key" handler fails while other handlers always
// return nil error.
func GoAndWait(handlers ...func() error) error {
	var (
		wg   sync.WaitGroup
		once sync.Once
		err  error
	)
	for _, f := range handlers {
		wg.Add(1)
		go func(handler func() error) {
			defer func() {
				if e := recover(); e != nil {
					buf := make([]byte, PanicBufLen)
					buf = buf[:runtime.Stack(buf, false)]
					log.Errorf("[PANIC]%v\n%s\n", e, buf)
				}
				wg.Done()
			}()
			if e := handler(); e != nil {
				once.Do(func() {
					err = e
				})
			}
		}(f)
	}
	wg.Wait()
	return err
}
