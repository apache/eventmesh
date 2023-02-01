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

package task

import (
	"context"
	"fmt"
	catalog "github.com/apache/incubator-eventmesh/eventmesh-catalog-go/api/proto"
	pconfig "github.com/apache/incubator-eventmesh/eventmesh-server-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/naming/selector"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/flow"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/constants"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/dal"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/dal/model"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/protocol"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/queue"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	"net"
)

type Task interface {
	Run() error
}

type baseTask struct {
	taskID             string
	taskInstanceID     string
	workflowID         string
	workflowInstanceID string
	input              string
	taskType           string
	queue              queue.ObserveQueue
	workflowDAL        dal.WorkflowDAL
}

func New(instance *model.WorkflowTaskInstance) Task {
	if instance == nil || instance.Task == nil {
		return nil
	}
	switch instance.Task.TaskType {
	case constants.TaskTypeOperation:
		return NewOperationTask(instance)
	case constants.TaskTypeEvent:
		return NewEventTask(instance)
	case constants.TaskTypeSwitch:
		return NewSwitchTask(instance)
	}
	return nil
}

func publishEvent(workflowInstanceID string, taskInstanceID string, operationID string, content string) error {
	eventCatalog, err := queryPublishCatalog(operationID)
	if err != nil {
		return err
	}
	return protocol.Builder(config.Get().Flow.Protocol).Publish(context.Background(), eventCatalog.Topic,
		content,
		map[string]string{constants.EventPropsWorkflowInstanceID: workflowInstanceID,
			constants.EventPropsWorkflowTaskInstanceID: taskInstanceID})
}

func queryPublishCatalog(operationID string) (*flow.WorkflowEventCatalog, error) {
	grpcConn, err := getGRPCConn()
	if err != nil {
		return nil, err
	}
	defer closeGRPCConn(grpcConn)

	catalogClient := catalog.NewCatalogClient(grpcConn)
	rsp, err := catalogClient.QueryOperations(context.Background(), &catalog.QueryOperationsRequest{
		OperationId: operationID,
	})
	if err != nil {
		return nil, err
	}
	if len(rsp.Operations) == 0 {
		return nil, fmt.Errorf("operationID %s invalid, please check it", operationID)
	}
	operation := rsp.Operations[0]
	if operation.Type != constants.EventTypePublish {
		return nil, fmt.Errorf("operationID %s invalid, please check it", operationID)
	}
	return &flow.WorkflowEventCatalog{Topic: operation.ChannelName, Schema: operation.Schema,
		OperationID: operationID}, nil
}

func getGRPCConn() (*grpc.ClientConn, error) {
	namingClient := selector.Get(pconfig.GlobalConfig().Server.Name)
	if namingClient == nil {
		return nil, fmt.Errorf("naming client is not registered.please register it")
	}
	instance, err := namingClient.Select(config.Get().Catalog.ServerName)
	if err != nil {
		return nil, err
	}
	if instance == nil {
		return nil, fmt.Errorf("catalog client is not running.please check it")
	}
	host, port, err := net.SplitHostPort(instance.Address)
	if err != nil {
		return nil, err
	}
	addr := fmt.Sprintf("%v:%v", host, port)
	conn, err := grpc.Dial(addr, grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		return nil, err
	}
	log.Get(constants.LogSchedule).Infof("success make grpc conn with:%s", addr)
	return conn, nil
}

func closeGRPCConn(conn *grpc.ClientConn) {
	if conn != nil {
		if err := conn.Close(); err != nil {
			log.Get(constants.LogSchedule).Errorf("close grpc conn error:%v", err)
		}
	}
}
