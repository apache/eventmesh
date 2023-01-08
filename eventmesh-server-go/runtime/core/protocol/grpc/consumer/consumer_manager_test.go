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

package consumer

import (
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/config"
	"github.com/golang/mock/gomock"
	"testing"
	"time"

	"github.com/liyue201/gostl/ds/set"
	"github.com/stretchr/testify/assert"

	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/common/protocol/grpc"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/util"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin"
	_ "github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/connector/standalone"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/consts"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/protocol/grpc/emitter"
	emitermock "github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/protocol/grpc/emitter/mocks"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
)

func Test_NewConsumerManager(t *testing.T) {
	mgr, err := NewConsumerManager()
	assert.NoError(t, err)
	assert.NotNil(t, mgr)
}

func Test_GetConsumer(t *testing.T) {
	tests := []struct {
		name   string
		expect func(t *testing.T, mgr ConsumerManager)
	}{
		{
			name: "add one consumer",
			expect: func(t *testing.T, mgr ConsumerManager) {
				mesh, err := mgr.GetConsumer("consumergroup")
				assert.NoError(t, err)
				assert.NotNil(t, mesh)
			},
		},
		{
			name: "return exist one",
			expect: func(t *testing.T, mgr ConsumerManager) {
				mesh, err := mgr.GetConsumer("consumergroup")
				assert.NoError(t, err)
				assert.NotNil(t, mesh)

				mesh2, err := mgr.GetConsumer("consumergroup")
				assert.NoError(t, err)
				assert.NotNil(t, mesh2)
				assert.Equal(t, mesh, mesh2)
			},
		},
	}

	plugin.SetActivePlugin(map[string]string{
		"connector": "standalone",
	})
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			mgr, err := NewConsumerManager()
			assert.NoError(t, err)
			assert.NotNil(t, mgr)
			tc.expect(t, mgr)
		})
	}
}

func Test_RegisterClient(t *testing.T) {
	getClientInConsumer := func(cg string, mgr ConsumerManager) (*GroupClient, bool) {
		cm := mgr.(*consumerManager)
		v, ok := cm.consumerGroupClients.Load(cg)
		if !ok {
			return nil, false
		}
		return v.(*set.Set).First().Value().(*GroupClient), true
	}
	tests := []struct {
		name   string
		expect func(t *testing.T, mgr ConsumerManager)
	}{
		{
			name: "register new",
			expect: func(t *testing.T, mgr ConsumerManager) {
				err := mgr.RegisterClient(&GroupClient{
					ENV:              "env",
					IDC:              "IDC",
					ConsumerGroup:    "ConsumerGroup",
					Topic:            "Topic",
					GRPCType:         consts.WEBHOOK,
					URL:              "http://test.com",
					SubscriptionMode: pb.Subscription_SubscriptionItem_CLUSTERING,
					SYS:              "SYS",
					IP:               "IP",
					PID:              util.PID(),
					Hostname:         "test",
					APIVersion:       "v1",
					LastUPTime:       time.Now(),
					Emiter:           emitter.NewEventEmitter(nil),
				})
				assert.NoError(t, err)
			},
		},
		{
			name: "webhook register exist and update time",
			expect: func(t *testing.T, mgr ConsumerManager) {
				firstTime := time.Now()
				oldUrl := "http://old.test.com"
				cli := &GroupClient{
					ENV:              "env",
					IDC:              "IDC",
					ConsumerGroup:    "ConsumerGroup",
					Topic:            "Topic",
					GRPCType:         consts.WEBHOOK,
					URL:              oldUrl,
					SubscriptionMode: pb.Subscription_SubscriptionItem_CLUSTERING,
					SYS:              "SYS",
					IP:               "IP",
					PID:              util.PID(),
					Hostname:         "test",
					APIVersion:       "v1",
					LastUPTime:       firstTime,
					Emiter:           emitter.NewEventEmitter(nil),
				}
				assert.NoError(t, mgr.RegisterClient(cli))
				time.Sleep(time.Second)
				cli.URL = "http://new.test.com"
				cli.LastUPTime = time.Now()
				assert.NoError(t, mgr.RegisterClient(cli))
				cliInMgr, ok := getClientInConsumer(cli.ConsumerGroup, mgr)
				assert.True(t, ok)
				assert.NotEqual(t, cliInMgr.LastUPTime.Second(), firstTime.Second())
				assert.Equal(t, cliInMgr.URL, "http://new.test.com")
			},
		},
		{
			name: "stream register exist and update time",
			expect: func(t *testing.T, mgr ConsumerManager) {
				mockctl := gomock.NewController(t)
				oldEmiter := emitermock.NewMockEventEmitter(mockctl)
				oldEmiter.EXPECT().SendStreamResp(&pb.RequestHeader{}, &grpc.StatusCode{}).Return(nil).Times(1)
				firstTime := time.Now()
				cli := &GroupClient{
					ENV:              "env",
					IDC:              "IDC",
					ConsumerGroup:    "ConsumerGroup",
					Topic:            "Topic",
					GRPCType:         consts.WEBHOOK,
					SubscriptionMode: pb.Subscription_SubscriptionItem_CLUSTERING,
					SYS:              "SYS",
					IP:               "IP",
					PID:              util.PID(),
					Hostname:         "test",
					APIVersion:       "v1",
					LastUPTime:       firstTime,
					Emiter:           oldEmiter,
				}
				assert.NoError(t, mgr.RegisterClient(cli))
				time.Sleep(time.Second)
				newEmiter := emitermock.NewMockEventEmitter(mockctl)
				newEmiter.EXPECT().SendStreamResp(&pb.RequestHeader{}, &grpc.StatusCode{}).Return(fmt.Errorf("error")).Times(1)
				cli.Emiter = newEmiter
				cli.LastUPTime = time.Now()
				assert.NoError(t, mgr.RegisterClient(cli))
				cliInMgr, ok := getClientInConsumer(cli.ConsumerGroup, mgr)
				assert.True(t, ok)
				assert.NotEqual(t, cliInMgr.LastUPTime.Second(), firstTime.Second())
				assert.Nil(t, oldEmiter.SendStreamResp(&pb.RequestHeader{}, &grpc.StatusCode{}))
				assert.Error(t, cliInMgr.Emiter.SendStreamResp(&pb.RequestHeader{}, &grpc.StatusCode{}))
			},
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			mgr, err := NewConsumerManager()
			assert.NoError(t, err)
			assert.NotNil(t, mgr)
			tc.expect(t, mgr)
		})
	}
}

func Test_DeRegisterClient(t *testing.T) {
	tests := []struct {
		name   string
		expect func(t *testing.T, mgr ConsumerManager)
	}{
		{
			name: "load consumer not exist",
			expect: func(t *testing.T, mgr ConsumerManager) {
				err := mgr.DeRegisterClient(&GroupClient{
					ConsumerGroup: "not exist",
				})
				assert.Nil(t, err)
			},
		},
		{
			name: "deregister exist",
			expect: func(t *testing.T, mgr ConsumerManager) {
				mockctl := gomock.NewController(t)
				newEmiter := emitermock.NewMockEventEmitter(mockctl)
				cli := &GroupClient{
					ENV:              "env",
					IDC:              "IDC",
					ConsumerGroup:    "ConsumerGroup",
					Topic:            "Topic",
					GRPCType:         consts.WEBHOOK,
					SubscriptionMode: pb.Subscription_SubscriptionItem_CLUSTERING,
					SYS:              "SYS",
					IP:               "IP",
					PID:              util.PID(),
					Hostname:         "test",
					APIVersion:       "v1",
					LastUPTime:       time.Now(),
					Emiter:           newEmiter,
				}
				err := mgr.RegisterClient(cli)
				assert.NoError(t, err)
				err = mgr.DeRegisterClient(cli)
				assert.NoError(t, err)
			},
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			mgr, err := NewConsumerManager()
			assert.NoError(t, err)
			assert.NotNil(t, mgr)
			tc.expect(t, mgr)
		})
	}
}

func Test_UpdateClientTime(t *testing.T) {
	getClientInConsumer := func(cg string, mgr ConsumerManager) (*GroupClient, bool) {
		cm := mgr.(*consumerManager)
		v, ok := cm.consumerGroupClients.Load(cg)
		if !ok {
			return nil, false
		}
		return v.(*set.Set).First().Value().(*GroupClient), true
	}
	tests := []struct {
		name   string
		expect func(t *testing.T, mgr ConsumerManager)
	}{
		{
			name: "update consumer not exist",
			expect: func(t *testing.T, mgr ConsumerManager) {
				mgr.UpdateClientTime(&GroupClient{
					ConsumerGroup: "not exist",
				})
			},
		},
		{
			name: "update consumer exist",
			expect: func(t *testing.T, mgr ConsumerManager) {
				mockctl := gomock.NewController(t)
				newEmiter := emitermock.NewMockEventEmitter(mockctl)
				firstTime := time.Now()
				cli := &GroupClient{
					ENV:              "env",
					IDC:              "IDC",
					ConsumerGroup:    "ConsumerGroup",
					Topic:            "Topic",
					GRPCType:         consts.WEBHOOK,
					SubscriptionMode: pb.Subscription_SubscriptionItem_CLUSTERING,
					SYS:              "SYS",
					IP:               "IP",
					PID:              util.PID(),
					Hostname:         "test",
					APIVersion:       "v1",
					LastUPTime:       firstTime,
					Emiter:           newEmiter,
				}
				err := mgr.RegisterClient(cli)
				assert.NoError(t, err)
				time.Sleep(time.Second * 3)
				mgr.UpdateClientTime(cli)
				existCli, ok := getClientInConsumer(cli.ConsumerGroup, mgr)
				assert.True(t, ok)
				assert.NotEqual(t, existCli.LastUPTime.UnixMilli(), firstTime.UnixMilli())
			},
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			mgr, err := NewConsumerManager()
			assert.NoError(t, err)
			assert.NotNil(t, mgr)
			tc.expect(t, mgr)
		})
	}
}

func Test_RestartConsumer(t *testing.T) {
	err := config.GlobalConfig().Plugins.Setup()
	assert.NoError(t, err)
	plugin.SetActivePlugin(config.GlobalConfig().ActivePlugins)
	cli := &GroupClient{
		ENV:              "env",
		IDC:              "IDC",
		ConsumerGroup:    "ConsumerGroup",
		Topic:            "Topic",
		GRPCType:         consts.WEBHOOK,
		SubscriptionMode: pb.Subscription_SubscriptionItem_CLUSTERING,
		SYS:              "SYS",
		IP:               "IP",
		PID:              util.PID(),
		Hostname:         "test",
		APIVersion:       "v1",
		LastUPTime:       time.Now(),
		Emiter:           nil,
	}
	tests := []struct {
		name   string
		expect func(t *testing.T, mgr ConsumerManager)
	}{
		{
			name: "consumer group not found",
			expect: func(t *testing.T, mgr ConsumerManager) {
				err := mgr.RestartConsumer("not exist consumer group")
				assert.NoError(t, err)
			},
		},
		{
			name: "state is running",
			expect: func(t *testing.T, mgr ConsumerManager) {
				err := mgr.RegisterClient(cli)
				assert.NoError(t, err)
				mesh, err := mgr.GetConsumer(cli.ConsumerGroup)
				assert.NoError(t, err)
				assert.NotNil(t, mesh)
				ok := mesh.RegisterClient(cli)
				assert.True(t, ok)
				err = mgr.RestartConsumer(cli.ConsumerGroup)
				assert.Equal(t, mesh.ServiceState(), consts.RUNNING)
				assert.NoError(t, err)

			},
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			mgr, err := NewConsumerManager()
			assert.NoError(t, err)
			assert.NotNil(t, mgr)
			tc.expect(t, mgr)
		})
	}
}
