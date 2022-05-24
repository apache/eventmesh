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
