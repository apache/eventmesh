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

package queue

import (
	"context"
	"encoding/json"
	"fmt"
	sdk "github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc"
	sdk_conf "github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/conf"
	sdk_pb "github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/proto"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/metrics"
	"github.com/gogf/gf/util/gconv"
	"github.com/google/uuid"

	conf "github.com/apache/incubator-eventmesh/eventmesh-workflow-go/config"

	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/constants"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/dal"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/dal/model"
)

func init() {
	cfg := conf.Get()
	// init and register EventMesh task queue only when config corresponding queue type
	if cfg != nil && cfg.Flow.Queue.Store == constants.QueueTypeEventMesh {
		RegisterQueue(newEventMeshQueue(cfg))
	}
}

type eventMeshQueue struct {
	// EventMesh go sdk grpc client
	grpcClient sdk.Interface
	// EventMesh go sdk grpc config
	grpcConfig *sdk_conf.GRPCConfig

	workflowConfig *conf.Config
	workflowDAL    dal.WorkflowDAL
	observeTopic   string
}

func newEventMeshQueue(workflowConfig *conf.Config) ObserveQueue {
	eventMeshConfig := workflowConfig.EventMesh
	grpcConfig := &sdk_conf.GRPCConfig{
		Host:         eventMeshConfig.Host,
		Port:         eventMeshConfig.GRPC.Port,
		ENV:          eventMeshConfig.Env,
		IDC:          eventMeshConfig.IDC,
		SYS:          eventMeshConfig.Sys,
		Username:     eventMeshConfig.UserName,
		Password:     eventMeshConfig.Password,
		ProtocolType: sdk.EventmeshMessage,
		ConsumerConfig: sdk_conf.ConsumerConfig{
			Enabled:       true,
			ConsumerGroup: eventMeshConfig.ConsumerGroup,
		},
	}
	client, err := sdk.New(grpcConfig)
	if err != nil {
		log.Get(constants.LogQueue).Errorf("EventMesh task queue, fail to init EventMesh client , error=%v", err)
		panic(err)
	}
	return &eventMeshQueue{
		grpcClient:     client,
		grpcConfig:     grpcConfig,
		workflowConfig: workflowConfig,
		observeTopic:   workflowConfig.Flow.Queue.Topic,
		workflowDAL:    dal.NewWorkflowDAL(),
	}
}

func (q *eventMeshQueue) Name() string {
	return constants.QueueTypeEventMesh
}

// Publish send task to EventMesh queue, store task info in message content with json structure
func (q *eventMeshQueue) Publish(tasks []*model.WorkflowTaskInstance) error {
	if len(tasks) == 0 {
		return nil
	}
	for _, task := range tasks {
		if task == nil {
			continue
		}
		message, err := q.toEventMeshMessage(task)
		if err != nil {
			log.Get(constants.LogQueue).Errorf("EventMesh task queue, fail to publish task, error=%v", err)
			return err
		}
		_, err = q.grpcClient.Publish(context.Background(), message)
		if err != nil {
			log.Get(constants.LogQueue).Errorf("EventMesh task queue, fail to publish task, error=%v", err)
			return err
		}
		metrics.Inc(constants.MetricsTaskQueue, fmt.Sprintf("%s_%s", q.Name(), constants.MetricsQueueSize))
	}
	return nil
}

// Ack do nothing
func (q *eventMeshQueue) Ack(tasks *model.WorkflowTaskInstance) error {
	return nil
}

// Observe consume task by EventMesh subscription api
func (q *eventMeshQueue) Observe() {
	err := q.grpcClient.SubscribeStream(sdk_conf.SubscribeItem{
		Topic:         q.observeTopic,
		SubscribeMode: sdk_conf.CLUSTERING,
		SubscribeType: sdk_conf.SYNC,
	}, q.handler)
	if err != nil {
		log.Get(constants.LogQueue).Errorf("EventMesh task queue observe error=%v", err)
		panic(err)
	}
}

func (q *eventMeshQueue) handler(message *sdk_pb.SimpleMessage) interface{} {
	metrics.Dec(constants.MetricsTaskQueue, fmt.Sprintf("%s_%s", q.Name(), constants.MetricsQueueSize))
	workflowTask, err := q.toWorkflowTask(message)
	if err != nil {
		return err
	}
	log.Get(constants.LogQueue).Infof("receive task from EventMesh queue, task=%s", gconv.String(workflowTask))
	if workflowTask.ID != 0 {
		if err := q.workflowDAL.UpdateTaskInstance(dal.GetDalClient(), workflowTask); err != nil {
			log.Get(constants.LogQueue).Errorf("EventMesh task queue observe UpdateTaskInstance error=%v", err)
		}
		return err
	}
	// new task
	if err := q.workflowDAL.InsertTaskInstance(context.Background(), workflowTask); err != nil {
		log.Get(constants.LogQueue).Errorf("EventMesh task queue observe InsertTaskInstance error=%v", err)
	}
	return nil
}

func (q *eventMeshQueue) toEventMeshMessage(task *model.WorkflowTaskInstance) (*sdk_pb.SimpleMessage, error) {
	taskJsonBytes, err := json.Marshal(task)
	if err != nil {
		return nil, err
	}

	message := &sdk_pb.SimpleMessage{
		Header:        sdk.CreateHeader(q.grpcConfig),
		ProducerGroup: q.workflowConfig.EventMesh.ProducerGroup,
		Topic:         q.observeTopic,
		Content:       string(taskJsonBytes),
		Ttl:           gconv.String(q.workflowConfig.EventMesh.TTL),
		UniqueId:      uuid.New().String(),
		SeqNum:        uuid.New().String(),
	}
	return message, nil
}

func (q *eventMeshQueue) toWorkflowTask(message *sdk_pb.SimpleMessage) (*model.WorkflowTaskInstance, error) {
	taskJsonBytes := []byte(message.Content)
	task := &model.WorkflowTaskInstance{}
	err := json.Unmarshal(taskJsonBytes, task)
	if err != nil {
		return nil, err
	}
	return task, nil
}
