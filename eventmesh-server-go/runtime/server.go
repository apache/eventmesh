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

package runtime

import (
	"context"

	"go.uber.org/fx"

	"github.com/apache/incubator-eventmesh/eventmesh-server-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/emserver"
)

// Server server for all eventmesh endpoint, include grpc/tcp/http servers
type Server struct {
	// servers for eventmesh
	servers []emserver.GracefulServer
}

// Start create and start all server
func Start() error {
	var (
		initSuccessed bool
		gracesrvs     []emserver.GracefulServer
	)

	defer func() {
		// if init failed, we need to stop the server already started
		if !initSuccessed && len(gracesrvs) > 0 {
			for _, srv := range gracesrvs {
				srv.Stop()
			}
		}
	}()

	if config.GlobalConfig().Server.TCPOption != nil {
		tcpserver, err := emserver.NewTCPServer(config.GlobalConfig().Server.TCPOption)
		if err != nil {
			return err
		}
		gracesrvs = append(gracesrvs, tcpserver)
	}
	if config.GlobalConfig().Server.GRPCOption != nil {
		grpcserver, err := emserver.NewGRPCServer(config.GlobalConfig().Server.GRPCOption)
		if err != nil {
			return err
		}
		gracesrvs = append(gracesrvs, grpcserver)
	}
	if config.GlobalConfig().Server.HTTPOption != nil {
		httpserver, err := emserver.NewHTTPServer(config.GlobalConfig().Server.HTTPOption)
		if err != nil {
			return err
		}
		gracesrvs = append(gracesrvs, httpserver)
	}
	srv := &Server{
		servers: gracesrvs,
	}
	app := fx.New(
		fx.Invoke(register),
		fx.Provide(func() *Server {
			return srv
		}),
	)

	initSuccessed = true
	return app.Start(context.TODO())
}

func register(lifecycle fx.Lifecycle, srv *Server) {
	for _, srv := range srv.servers {
		rs := srv
		lifecycle.Append(fx.Hook{
			OnStart: func(ctx context.Context) error {
				return rs.Serve()
			},
			OnStop: func(ctx context.Context) error {
				return rs.Stop()
			},
		})
	}
}
