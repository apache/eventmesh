package main

import (
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	_ "github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/naming/registry"
	_ "github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/database/mysql"
	_ "github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/naming/nacos/selector"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/api"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/api/proto"
	_ "github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/queue"
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
