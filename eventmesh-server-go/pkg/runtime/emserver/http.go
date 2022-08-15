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
	"github.com/gin-contrib/pprof"
	"github.com/gin-gonic/gin"
	"github.com/unrolled/secure"

	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/runtime/option"
)

// HTTPServer http server to handle eventmesh message
// from client send by http request
type HTTPServer struct {
	*Server

	// httpOption option for current http server
	httpOption *option.HTTPOption

	// router gin router to dispatch the http request
	router *gin.Engine
}

// NewHTTPServer create new http server by Gin
func NewHTTPServer(httpOption *option.HTTPOption) (GracefulServer, error) {
	r := gin.New()
	if httpOption.PProfOption != nil {
		log.Infof("enable pprof on http server, listen port:%v", httpOption.Port)
		pprof.Register(r)
	}
	if !httpOption.TLSOption.EnableInsecure {
		r.Use(TLSHandler())
	}

	return &HTTPServer{
		Server: &Server{
			Port: httpOption.Port,
		},
		router:     r,
		httpOption: httpOption,
	}, nil
}

func (h *HTTPServer) Serve() error {
	if h.httpOption.TLSOption.EnableInsecure {
		if err := h.router.RunTLS(h.Port,
			h.httpOption.TLSOption.Certfile,
			h.httpOption.TLSOption.Keyfile); err != nil {
			return err
		}
	}
	return h.router.Run(h.Port)
}

func (h *HTTPServer) Stop() error {

	return nil
}

// TLSHandler setup the https on http server
func TLSHandler() gin.HandlerFunc {
	return func(c *gin.Context) {
		secureMiddleware := secure.New(secure.Options{
			SSLRedirect: true,
			SSLHost:     "localhost:8080",
		})
		err := secureMiddleware.Process(c.Writer, c.Request)

		// If there was an error, do not continue.
		if err != nil {
			log.Fatalf("err in enable tls in https server, err:%v", err)
			return
		}

		c.Next()
	}
}
