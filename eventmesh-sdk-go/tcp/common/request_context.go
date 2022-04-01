package common

import (
	"eventmesh/common/protocol/tcp"
	"sync"
)

type RequestContext struct {
	key      interface{}
	request  tcp.Package
	response tcp.Package
	wg       sync.WaitGroup
}

func (r *RequestContext) Key() interface{} {
	return r.key
}

func (r *RequestContext) SetKey(key interface{}) {
	r.key = key
}

func (r *RequestContext) Request() tcp.Package {
	return r.request
}

func (r *RequestContext) SetRequest(request tcp.Package) {
	r.request = request
}

func (r *RequestContext) Response() tcp.Package {
	return r.response
}

func (r *RequestContext) SetResponse(response tcp.Package) {
	r.response = response
}

func (r *RequestContext) Wg() sync.WaitGroup {
	return r.wg
}

func (r *RequestContext) SetWg(wg sync.WaitGroup) {
	r.wg = wg
}

func (r *RequestContext) Finish(message tcp.Package) {
	r.response = message
	//r.wg.Done()
}

func GetRequestContextKey(request tcp.Package) interface{} {
	return request.Header.Seq
}

func NewRequestContext(key interface{}, request tcp.Package, latch int) *RequestContext {
	ctx := &RequestContext{key: key, request: request}
	//ctx.Wg().Add(latch)
	return ctx
}
