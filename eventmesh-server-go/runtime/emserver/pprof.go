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

package emserver

import (
	"context"
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/config"
	"net/http"
	"net/http/pprof"
)

type PProfServer struct {
	httpSrv     *http.Server
	pprofOption *config.PProfOption
}

func NewPProfServer(opt *config.PProfOption) GracefulServer {
	mux := http.NewServeMux()

	mux.HandleFunc("/debug/pprof/", pprof.Index)
	mux.HandleFunc("/debug/pprof/cmdline", pprof.Cmdline)
	mux.HandleFunc("/debug/pprof/profile", pprof.Profile)
	mux.HandleFunc("/debug/pprof/symbol", pprof.Symbol)
	mux.HandleFunc("/debug/pprof/trace", pprof.Trace)

	return &PProfServer{
		pprofOption: opt,
		httpSrv: &http.Server{
			Addr:    fmt.Sprintf(":%v", opt.Port),
			Handler: mux,
		},
	}
}

func (p *PProfServer) Serve() error {
	if p.pprofOption.TLSOption != nil {
		return p.httpSrv.ListenAndServeTLS(
			p.pprofOption.Certfile,
			p.pprofOption.Keyfile,
		)
	}
	return p.httpSrv.ListenAndServe()
}

func (p *PProfServer) Stop() error {
	return p.httpSrv.Shutdown(context.TODO())
}
