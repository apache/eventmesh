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
	"testing"
	"time"

	"github.com/golang/mock/gomock"
	"github.com/stretchr/testify/assert"

	"github.com/apache/incubator-eventmesh/eventmesh-server-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/util"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/consts"
	wappermock "github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/wrapper/mocks"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
)

func Test_init(t *testing.T) {
	tests := []struct {
		name   string
		expect func(t *testing.T, mesh EventMeshConsumer)
	}{
		{
			name: "consumer group list empty",
			expect: func(t *testing.T, mesh EventMeshConsumer) {
				assert.NoError(t, mesh.Init())
			},
		},
		{
			name: "init success",
			expect: func(t *testing.T, mesh EventMeshConsumer) {
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
				ok := mesh.RegisterClient(cli)
				assert.True(t, ok)
				err := mesh.Init()
				assert.NoError(t, err)
				assert.Equal(t, mesh.ServiceState(), consts.INITED)
			},
		},
	}

	err := config.GlobalConfig().Plugins.Setup()
	assert.NoError(t, err)
	plugin.SetActivePlugin(config.GlobalConfig().ActivePlugins)
	for _, tc := range tests {
		mesh, err := NewEventMeshConsumer("test-consumergroup")
		assert.NoError(t, err)
		t.Run(tc.name, func(t *testing.T) {
			tc.expect(t, mesh)
		})
	}
}

func Test_Start(t *testing.T) {
	tests := []struct {
		name   string
		expect func(t *testing.T, mesh EventMeshConsumer)
	}{
		{
			name: "consumer group empty",
			expect: func(t *testing.T, mesh EventMeshConsumer) {
				err := mesh.Start()
				assert.NoError(t, err)
			},
		},
		{
			name: "broadcast consumer start fail",
			expect: func(t *testing.T, mesh EventMeshConsumer) {
				assert.NoError(t, mesh.Init())
				originMesh := mesh.(*eventMeshConsumer)
				mockctl := gomock.NewController(t)
				mockConsumerWrapper := wappermock.NewMockConsumer(mockctl)
				mockerr := fmt.Errorf("mock broadcase err")
				mockConsumerWrapper.EXPECT().Start().Return(mockerr).AnyTimes()
				originMesh.broadcastConsumer = mockConsumerWrapper

				gropCli := DefaultWebhookGroupClient()
				mesh.RegisterClient(gropCli)
				err := mesh.Start()
				assert.Error(t, err)
				assert.Equal(t, err, mockerr)
			},
		},
		{
			name: "persistent consumer start fail",
			expect: func(t *testing.T, mesh EventMeshConsumer) {
				assert.NoError(t, mesh.Init())
				originMesh := mesh.(*eventMeshConsumer)
				mockctl := gomock.NewController(t)
				mockConsumerWrapper := wappermock.NewMockConsumer(mockctl)
				mockerr := fmt.Errorf("mock persistend err")
				mockConsumerWrapper.EXPECT().Start().Return(mockerr).AnyTimes()
				mockConsumerWrapper.EXPECT().Subscribe("Topic").Return(nil).AnyTimes()
				originMesh.persistentConsumer = mockConsumerWrapper

				gropCli := DefaultWebhookGroupClient()
				mesh.RegisterClient(gropCli)
				err := mesh.Start()
				assert.Error(t, err)
				assert.Equal(t, err, mockerr)
			},
		},
		{
			name: "start success",
			expect: func(t *testing.T, mesh EventMeshConsumer) {
				assert.NoError(t, mesh.Init())
				originMesh := mesh.(*eventMeshConsumer)
				mockctl := gomock.NewController(t)
				mockConsumerpersistentWrapper := wappermock.NewMockConsumer(mockctl)
				mockConsumerpersistentWrapper.EXPECT().Start().Return(nil).AnyTimes()
				mockConsumerpersistentWrapper.EXPECT().Subscribe("Topic").Return(nil).AnyTimes()
				originMesh.persistentConsumer = mockConsumerpersistentWrapper

				mockConsumerbroadcastWrapper := wappermock.NewMockConsumer(mockctl)
				mockConsumerbroadcastWrapper.EXPECT().Start().Return(nil).AnyTimes()
				mockConsumerbroadcastWrapper.EXPECT().Subscribe("Topic").Return(nil).AnyTimes()

				originMesh.broadcastConsumer = mockConsumerbroadcastWrapper

				gropCli := DefaultWebhookGroupClient()
				mesh.RegisterClient(gropCli)
				err := mesh.Start()
				assert.NoError(t, err)
			},
		},
	}
	err := config.GlobalConfig().Plugins.Setup()
	assert.NoError(t, err)
	plugin.SetActivePlugin(config.GlobalConfig().ActivePlugins)
	for _, tc := range tests {
		mesh, err := NewEventMeshConsumer("test-consumergroup")
		assert.NoError(t, err)
		t.Run(tc.name, func(t *testing.T) {
			tc.expect(t, mesh)
		})
	}
}

func Test_ServiceState(t *testing.T) {
	tests := []struct {
		name   string
		expect func(t *testing.T, mesh EventMeshConsumer)
	}{
		{
			name: "init",
			expect: func(t *testing.T, mesh EventMeshConsumer) {
				cli := DefaultWebhookGroupClient()
				ok := mesh.RegisterClient(cli)
				assert.True(t, ok)
				err := mesh.Init()
				assert.NoError(t, err)
				assert.Equal(t, mesh.ServiceState(), consts.INITED)
			},
		},
		{
			name: "running",
			expect: func(t *testing.T, mesh EventMeshConsumer) {
				cli := DefaultWebhookGroupClient()
				ok := mesh.RegisterClient(cli)
				assert.True(t, ok)
				err := mesh.Init()
				assert.NoError(t, err)
				assert.Equal(t, mesh.ServiceState(), consts.INITED)
				assert.NoError(t, mesh.Start())
				assert.Equal(t, mesh.ServiceState(), consts.RUNNING)
			},
		},
		{
			name: "stopped",
			expect: func(t *testing.T, mesh EventMeshConsumer) {
				cli := DefaultWebhookGroupClient()
				ok := mesh.RegisterClient(cli)
				assert.True(t, ok)
				err := mesh.Init()
				assert.NoError(t, err)
				assert.Equal(t, mesh.ServiceState(), consts.INITED)
				assert.NoError(t, mesh.Start())
				assert.Equal(t, mesh.ServiceState(), consts.RUNNING)
				assert.NoError(t, mesh.Shutdown())
				assert.Equal(t, mesh.ServiceState(), consts.STOPED)
			},
		},
	}
	err := config.GlobalConfig().Plugins.Setup()
	assert.NoError(t, err)
	plugin.SetActivePlugin(config.GlobalConfig().ActivePlugins)
	for _, tc := range tests {
		mesh, err := NewEventMeshConsumer("test-consumergroup")
		assert.NoError(t, err)
		t.Run(tc.name, func(t *testing.T) {
			tc.expect(t, mesh)
		})
	}
}
