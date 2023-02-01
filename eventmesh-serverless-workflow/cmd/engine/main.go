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
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/naming/registry"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin"
	_ "github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/database/mysql"
	_ "github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/naming/nacos/registry"
	_ "github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/naming/nacos/selector"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/api"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/api/proto"
	pconfig "github.com/apache/incubator-eventmesh/eventmesh-workflow-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/constants"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/dal"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/queue"
	_ "github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/queue"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/schedule"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/util"
	"google.golang.org/grpc"
	"net"
)

type Server struct {
	server   *grpc.Server
	schedule schedule.Scheduler
	queue    queue.ObserveQueue
}

func main() {
	s, err := initServer()
	if err != nil {
		log.Fatal("flow new server fail: " + err.Error())
	}
	router(s)
	if err = s.run(); err != nil {
		log.Fatal("run server fail: " + err.Error())
	}
}

func router(s *Server) {
	proto.RegisterWorkflowServer(s.server, api.NewWorkflowService())
}

func initServer() (*Server, error) {
	plugin.Register(constants.LogSchedule, log.DefaultLogFactory)
	plugin.Register(constants.LogQueue, log.DefaultLogFactory)

	var s Server
	if err := s.setupConfig(); err != nil {
		return nil, err
	}
	reg := registry.Get(config.GlobalConfig().Server.Name)
	if reg == nil {
		return nil, errors.New("service name=" + config.GlobalConfig().Server.Name + " not find registry")
	}
	if err := reg.Register(config.GlobalConfig().Server.Name); err != nil {
		return nil, err
	}
	scheduler, err := schedule.NewScheduler()
	if err != nil {
		return nil, err
	}
	s.schedule = scheduler
	if err = dal.Open(); err != nil {
		return nil, err
	}
	s.queue = queue.GetQueue(config.GlobalConfig().Flow.Queue.Store)

	s.server = grpc.NewServer()
	return &s, nil
}

func (s *Server) run() error {
	s.queue.Observe()
	s.schedule.Run()

	l, err := s.listen()
	if err != nil {
		return err
	}
	return s.server.Serve(l)
}

func (s *Server) setupConfig() error {
	config.ServerConfigPath = "./configs/workflow.yaml"
	// compatible local environment
	if !util.Exists(config.ServerConfigPath) {
		config.ServerConfigPath = "../configs/workflow.yaml"
	}
	// compatible deploy environment
	if !util.Exists(config.ServerConfigPath) {
		config.ServerConfigPath = "../conf/workflow.yaml"
	}
	cfg, err := config.LoadConfig(config.ServerConfigPath)
	if err != nil {
		return err
	}
	config.SetGlobalConfig(cfg)
	if err := config.Setup(cfg); err != nil {
		return err
	}
	return pconfig.Setup(config.ServerConfigPath)
}

func (s *Server) listen() (net.Listener, error) {
	listener, err := net.Listen("tcp", fmt.Sprintf(":%d", config.GlobalConfig().Server.Port))
	if err != nil {
		return nil, err
	}
	return listener, nil
}
