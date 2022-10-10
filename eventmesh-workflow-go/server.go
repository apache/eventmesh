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
	"context"
	"errors"
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/naming/registry"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/api"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/api/proto"
	pconfig "github.com/apache/incubator-eventmesh/eventmesh-workflow-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/constants"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/dal"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/dal/model"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/queue"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/schedule"
	"github.com/gogf/gf/util/gconv"
	"google.golang.org/grpc"
	"net"
	"time"
)

type Server struct {
	Server   *grpc.Server
	schedule schedule.Scheduler
	queue    queue.ObserveQueue
}

func NewServer() (*Server, error) {
	plugin.Register(constants.LogSchedule, log.DefaultLogFactory)
	plugin.Register(constants.LogQueue, log.DefaultLogFactory)

	var s Server
	scheduler, err := schedule.NewScheduler()
	if err != nil {
		return nil, err
	}
	s.schedule = scheduler
	if err = s.SetupConfig(); err != nil {
		return nil, err
	}
	if err = dal.Open(); err != nil {
		return nil, err
	}
	s.queue = queue.GetQueue(config.GlobalConfig().Flow.Queue.Store)

	s.Server = grpc.NewServer()
	return &s, nil
}

func (s *Server) Run() error {
	s.queue.Observe()
	s.schedule.Run()

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
	// tmpInsert()
	//tmpExecute()
	return s.Server.Serve(l)
}

func (s *Server) SetupConfig() error {
	config.ServerConfigPath = "./configs/workflow.yaml"
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

func tmpExecute() {
	var s = api.NewWorkflowService()
	var req = proto.ExecuteRequest{}
	req.Id = "storeorderworkflow"
	req.Input = "{\n    \"orderNo\":\"1234233\",\n    \"amount\":\"123\",\n    \"itemName\":\"goodsname\",\n    \"buyerUserId\":\"buyerUserId\"\n}"
	r, err := s.Execute(context.Background(), &req)
	if err != nil {
		log.Infof("err=%v", err)
		return
	}
	log.Infof("result=%s", gconv.String(r))
}

func tmpInsert() {
	var wf = model.Workflow{}
	wf.WorkflowID = "storeorderworkflow"
	wf.Status = 1
	wf.Version = "1.0.0"
	wf.WorkflowName = "Store Order Management Workflow"
	wf.Definition = "id: storeorderworkflow\nversion: '1.0'\nspecVersion: '0.8'\nname: Store Order Management Workflow\nstart: Receive New Order Event\nstates:\n  - name: Receive New Order Event\n    type: event\n    onEvents:\n      - eventRefs:\n          - NewOrderEvent\n        actions:\n          - functionRef:\n              refName: \"OrderServiceSendEvent\"\n    transition: Check New Order Result\n  - name: Check New Order Result\n    type: switch\n    dataConditions:\n      - name: New Order Successfull\n        condition: \"${ .order.order_no != '' }\"\n        transition: Send Order Payment\n      - name: New Order Failed\n        condition: \"${ .order.order_no == '' }\"\n        end: true\n    defaultCondition:\n      end: true\n  - name: Send Order Payment\n    type: operation\n    actions:\n      - functionRef:\n          refName: \"PaymentServiceSendEvent\"\n    transition: Check Payment Status\n  - name: Check Payment Status\n    type: switch\n    dataConditions:\n      - name: Payment Successfull\n        condition: \"${ .payment.order_no != '' }\"\n        transition: Send Order Shipment\n      - name: Payment Denied\n        condition: \"${ .payment.order_no == '' }\"\n        end: true\n    defaultCondition:\n      end: true\n  - name: Send Order Shipment\n    type: operation\n    actions:\n      - functionRef:\n          refName: \"ShipmentServiceSendEvent\"\n    end: true\nevents:\n  - name: NewOrderEvent\n    source: store/order\n    type: online.store.newOrder\nfunctions:\n  - name: OrderServiceSendEvent\n    operation: file://orderService.yaml#sendOrder\n    type: asyncapi\n  - name: PaymentServiceSendEvent\n    operation: file://paymentService.yaml#sendPayment\n    type: asyncapi\n  - name: ShipmentServiceSendEvent\n    operation: file://shipmentService.yaml#sendShipment\n    type: asyncapi"
	wf.CreateTime = time.Now()
	wf.UpdateTime = time.Now()
	err := dal.NewWorkflowDAL().Insert(context.Background(), &wf)
	log.Info(err)
}
