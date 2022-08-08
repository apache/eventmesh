package main

import (
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/api"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/api/proto"
	_ "github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/naming/registry"
	_ "github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/queue"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/log"
	_ "github.com/apache/incubator-eventmesh/eventmesh-workflow-go/plugin/database/mysql"
	_ "github.com/apache/incubator-eventmesh/eventmesh-workflow-go/plugin/naming/nacos/selector"
)

func main() {
	s, err := NewServer()
	if err != nil {
		log.Fatal("flow new server fail: " + err.Error())
	}
	router(s)
	if err = s.Run(); err != nil {
		log.Fatal("run server fail: " + err.Error())
	}
}

func router(s *Server) {
	proto.RegisterWorkflowServer(s.Server, api.NewWorkflowService())
}
