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

package main

import (
	"errors"
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-catalog-go/internal/dal"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/naming/registry"
	"google.golang.org/grpc"
	"net"
)

type Server struct {
	Server *grpc.Server
}

func NewServer() (*Server, error) {
	var s Server
	if err := s.SetupConfig(); err != nil {
		return nil, err
	}
	if err := dal.Open(); err != nil {
		return nil, err
	}

	s.Server = grpc.NewServer()
	return &s, nil
}

func (s *Server) Run() error {
	l, err := s.listen()
	if err != nil {
		return err
	}
	reg := registry.Get(config.GlobalConfig().Server.Name)
	if reg == nil {
		return errors.New("service name=" + config.GlobalConfig().Server.Name + " not find registry")
	}
	if err = reg.Register(config.GlobalConfig().Server.Name); err != nil {
		return err
	}
	return s.Server.Serve(l)
}

func (s *Server) SetupConfig() error {
	config.ServerConfigPath = "./configs/catalog.yaml"
	cfg, err := config.LoadConfig(config.ServerConfigPath)
	if err != nil {
		return err
	}
	config.SetGlobalConfig(cfg)

	return config.Setup(cfg)
}

func (s *Server) listen() (net.Listener, error) {
	listener, err := net.Listen("tcp", fmt.Sprintf(":%d", config.GlobalConfig().Server.Port))
	if err != nil {
		return nil, err
	}
	return listener, nil
}
