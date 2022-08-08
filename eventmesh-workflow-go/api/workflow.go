package api

import (
	"context"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/flow"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/log"
	"github.com/gogf/gf/util/gconv"

	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/api/proto"
)

type workflowImpl struct {
	proto.UnimplementedWorkflowServer
	engine *flow.Engine
}

func NewWorkflowService() proto.WorkflowServer {
	var w workflowImpl
	w.engine = flow.NewEngine()
	return &w
}

func (s *workflowImpl) Execute(ctx context.Context, req *proto.ExecuteRequest) (*proto.ExecuteResponse, error) {
	var rsp proto.ExecuteResponse
	var param flow.WorkflowParam
	log.Info(gconv.String(req))
	if err := gconv.Struct(req, &param); err != nil {
		return nil, err
	}
	if len(req.InstanceId) != 0 {
		return &rsp, s.engine.Transition(ctx, &param)
	}
	r, err := s.engine.Start(ctx, &param)
	if err != nil {
		return nil, err
	}
	rsp.InstanceId = r
	return &rsp, nil
}
