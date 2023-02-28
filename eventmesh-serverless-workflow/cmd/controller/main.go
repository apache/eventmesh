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
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/config"
	_ "github.com/apache/incubator-eventmesh/eventmesh-workflow-go/cmd/controller/docs"
	pconfig "github.com/apache/incubator-eventmesh/eventmesh-workflow-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/dal"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/util"
	"github.com/gin-gonic/gin"
	swaggerFiles "github.com/swaggo/files"
	ginSwagger "github.com/swaggo/gin-swagger"
	"log"
	"net/http"
)

type Server struct {
	server   *gin.Engine
	workflow *WorkflowController
}

// @title           Workflow API
// @version         1.0
// @description     This is a workflow server.

// @license.name  Apache 2.0
// @license.url   http://www.apache.org/licenses/LICENSE-2.0.html
func main() {
	s, err := initServer()
	if err != nil {
		log.Fatal("flow new server fail: " + err.Error())
	}
	s.router()
	if err := s.run(); err != nil {
		log.Fatal("run server fail: " + err.Error())
	}
}

func initServer() (*Server, error) {
	var s Server
	if err := s.setupConfig(); err != nil {
		return nil, err
	}
	if err := dal.Open(); err != nil {
		return nil, err
	}
	r := gin.New()
	r.Use(cors()).Use(gin.Recovery())
	swagger(r)
	s.server = r
	s.workflow = NewWorkflowController()
	return &s, nil
}

func swagger(r *gin.Engine) {
	r.GET("/swagger/*any", ginSwagger.WrapHandler(swaggerFiles.Handler))
}

func cors() gin.HandlerFunc {
	return func(c *gin.Context) {
		method := c.Request.Method
		origin := c.Request.Header.Get("Origin")
		if origin != "" {
			c.Header("Access-Control-Allow-Origin", "*")
			c.Header("Access-Control-Allow-Methods", "POST, GET, OPTIONS, PUT, DELETE, UPDATE")
			c.Header("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, "+
				"Authorization")
			c.Header("Access-Control-Expose-Headers", "Content-Length, Access-Control-Allow-Origin, "+
				"Access-Control-Allow-Headers, Cache-Control, Content-Language, Content-Type")
			c.Header("Access-Control-Allow-Credentials", "true")
		}
		if method == "OPTIONS" {
			c.AbortWithStatus(http.StatusNoContent)
		}
		c.Next()
	}
}

func (s *Server) router() {
	s.server.POST("/workflow", s.workflow.Save)
	s.server.GET("/workflow", s.workflow.QueryList)
	s.server.GET("/workflow/:workflowId", s.workflow.QueryDetail)
	s.server.DELETE("/workflow/:workflowId", s.workflow.Delete)
	s.server.GET("/workflow/instances", s.workflow.QueryInstances)
}

func (s *Server) setupConfig() error {
	config.ServerConfigPath = "./configs/controller.yaml"
	// compatible local environment
	if !util.Exists(config.ServerConfigPath) {
		config.ServerConfigPath = "../configs/controller.yaml"
	}
	// compatible deploy environment
	if !util.Exists(config.ServerConfigPath) {
		config.ServerConfigPath = "../conf/controller.yaml"
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

func (s *Server) run() error {
	return s.server.Run(fmt.Sprintf(":%d", config.GlobalConfig().Server.Port))
}
