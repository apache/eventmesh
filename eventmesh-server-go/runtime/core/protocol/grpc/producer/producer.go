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

package producer

import (
	"fmt"
	config2 "github.com/apache/incubator-eventmesh/eventmesh-server-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/util"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/connector"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/consts"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/protocol/grpc/config"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/wrapper"
	"time"
)

type EventMeshProducer struct {
	cfg          *config.ProducerGroupConfig
	producer     *wrapper.Producer
	ServiceState consts.ServiceState
}

func NewEventMeshProducer(cfg *config.ProducerGroupConfig) (*EventMeshProducer, error) {
	pm, err := wrapper.NewProducer()
	if err != nil {
		return nil, err
	}

	cluster := config2.GlobalConfig().Server.GRPCOption.Cluster
	idc := config2.GlobalConfig().Server.GRPCOption.IDC
	mm := make(map[string]string)
	mm["producerGroup"] = cfg.GroupName
	mm["instanceName"] = util.BuildMeshClientID(cfg.GroupName, cluster)
	mm["eventMeshIDC"] = idc
	if err = pm.ProducerConnector.InitProducer(mm); err != nil {
		return nil, err
	}

	p := &EventMeshProducer{
		cfg:          cfg,
		producer:     pm,
		ServiceState: consts.INITED,
	}
	return p, nil
}

func (e *EventMeshProducer) Send(sctx SendMessageContext, callback *connector.SendCallback) error {
	return e.producer.Send(sctx.Ctx, sctx.Event, callback)
}

func (e *EventMeshProducer) Request(sctx SendMessageContext, callback *connector.RequestReplyCallback, timeout time.Duration) error {
	return e.producer.Request(sctx.Ctx, sctx.Event, callback, timeout)
}

func (e *EventMeshProducer) Reply(sctx SendMessageContext, callback *connector.SendCallback) error {
	return e.producer.Reply(sctx.Ctx, sctx.Event, callback)
}

func (e *EventMeshProducer) Start() error {
	if e.ServiceState == "" || e.ServiceState == consts.RUNNING {
		return nil
	}
	if err := e.producer.Start(); err != nil {
		return err
	}
	e.ServiceState = consts.RUNNING
	log.Info("start eventmesh producer for groupName:%s", e.cfg.GroupName)
	return nil
}

func (e *EventMeshProducer) Shutdown() error {
	if e.ServiceState == "" || e.ServiceState == consts.INITED {
		return nil
	}
	if err := e.producer.Shutdown(); err != nil {
		return err
	}
	e.ServiceState = consts.STOPED
	return nil
}

func (e *EventMeshProducer) Status() consts.ServiceState {
	return e.ServiceState
}

func (e *EventMeshProducer) String() string {
	return fmt.Sprintf("eventMeshProducer, status:%s,  groupName:%s", e.ServiceState, e.cfg.GroupName)
}
