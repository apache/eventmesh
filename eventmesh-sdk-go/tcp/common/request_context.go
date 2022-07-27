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

package common

import (
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/protocol/tcp"
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
