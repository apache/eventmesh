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

package nacos

import (
	"fmt"
	"github.com/nacos-group/nacos-sdk-go/v2/model"
	"os"
	"path/filepath"
	"testing"

	"github.com/golang/mock/gomock"
	"github.com/nacos-group/nacos-sdk-go/v2/vo"
	"github.com/stretchr/testify/assert"

	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/registry"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/registry/nacos/mocks"
)

func Test_Init(t *testing.T) {
	r := &Registry{}
	assert.NoError(t, r.Init())
	assert.True(t, r.initStatus.Load())
	assert.False(t, r.startStatus.Load())
}

func Test_Start(t *testing.T) {
	t.Run("create nameing client", func(t *testing.T) {
		cacheDir := filepath.Join(os.TempDir(), "nacos-test-cache-dir")
		assert.NoError(t, os.MkdirAll(cacheDir, os.ModePerm))
		defer os.RemoveAll(cacheDir)

		r := &Registry{
			cfg: &Config{
				ServiceName: "test",
				CacheDir:    "/tmp/",
				Port:        "8088",
				AddressList: "127.0.0.1:8081",
			},
		}
		assert.NoError(t, r.Init())
		ctrl := gomock.NewController(t)
		mocks.NewMockINamingClient(ctrl)
		assert.NoError(t, r.Start())
		assert.NoError(t, r.Shutdown())
		assert.False(t, r.initStatus.Load())
		assert.False(t, r.startStatus.Load())
	})
}

func Test_Register(t *testing.T) {
	cacheDir := filepath.Join(os.TempDir(), "nacos-test-cache-dir")
	assert.NoError(t, os.MkdirAll(cacheDir, os.ModePerm))
	defer os.RemoveAll(cacheDir)

	r := &Registry{
		cfg: &Config{
			ServiceName: "test",
			CacheDir:    "/tmp/",
			Port:        "8088",
			AddressList: "127.0.0.1:8081",
		},
	}
	assert.NoError(t, r.Init())
	assert.True(t, r.initStatus.Load())
	assert.False(t, r.startStatus.Load())
	assert.NoError(t, r.Start())
	ctrl := gomock.NewController(t)
	nameclient := mocks.NewMockINamingClient(ctrl)
	meshInfo := &registry.EventMeshRegisterInfo{
		EventMeshClusterName:    "test",
		EventMeshName:           "test",
		EndPoint:                "127.0.0.1:3333",
		EventMeshInstanceNumMap: map[string]map[string]int{},
		Metadata:                map[string]string{},
		ProtocolType:            "GRPC",
	}
	nameclient.EXPECT().RegisterInstance(vo.RegisterInstanceParam{
		Ip:          "127.0.0.1",
		Port:        3333,
		ServiceName: "test",
		GroupName:   uniqGroupName("GRPC"),
		Healthy:     true,
		Enable:      true,
		Weight:      DefaultWeight,
	}).Return(true, nil).Times(1)
	r.client = nameclient
	err := r.Register(meshInfo)
	assert.NoError(t, err)
	val, ok := r.registryInfos.Load(meshInfo.EventMeshName)
	assert.True(t, ok)
	assert.NotNil(t, val)
}

func Test_DeRegister(t *testing.T) {
	cacheDir := filepath.Join(os.TempDir(), "nacos-test-cache-dir")
	assert.NoError(t, os.MkdirAll(cacheDir, os.ModePerm))
	defer os.RemoveAll(cacheDir)

	r := &Registry{
		cfg: &Config{
			ServiceName: "test",
			CacheDir:    "/tmp/",
			Port:        "8088",
			AddressList: "127.0.0.1:8081",
		},
	}
	assert.NoError(t, r.Init())
	assert.True(t, r.initStatus.Load())
	assert.False(t, r.startStatus.Load())
	assert.NoError(t, r.Start())
	ctrl := gomock.NewController(t)
	nameclient := mocks.NewMockINamingClient(ctrl)
	meshInfo := &registry.EventMeshRegisterInfo{
		EventMeshClusterName:    "test",
		EventMeshName:           "test",
		EndPoint:                "127.0.0.1:3333",
		EventMeshInstanceNumMap: map[string]map[string]int{},
		Metadata:                map[string]string{},
		ProtocolType:            "GRPC",
	}
	nameclient.EXPECT().RegisterInstance(vo.RegisterInstanceParam{
		Ip:          "127.0.0.1",
		Port:        3333,
		ServiceName: "test",
		GroupName:   uniqGroupName("GRPC"),
		Healthy:     true,
		Enable:      true,
		Weight:      DefaultWeight,
	}).Return(true, nil).Times(1)
	r.client = nameclient
	err := r.Register(meshInfo)
	assert.NoError(t, err)
	val, ok := r.registryInfos.Load(meshInfo.EventMeshName)
	assert.True(t, ok)
	assert.NotNil(t, val)

	unmeshInfo := &registry.EventMeshUnRegisterInfo{
		EventMeshClusterName: "test",
		EventMeshName:        "test",
		EndPoint:             "127.0.0.1:3333",
		ProtocolType:         "GRPC",
	}
	nameclient.EXPECT().DeregisterInstance(vo.DeregisterInstanceParam{
		Ip:          "127.0.0.1",
		Port:        3333,
		ServiceName: meshInfo.EventMeshName,
		GroupName:   uniqGroupName("GRPC"),
	}).Return(true, nil).Times(1)
	err = r.UnRegister(unmeshInfo)
	assert.NoError(t, err)
	val, ok = r.registryInfos.Load(meshInfo.EventMeshName)
	assert.False(t, ok)
	assert.Nil(t, val)
}

func Test_FindEventMeshInfoByCluster(t *testing.T) {
	cacheDir := filepath.Join(os.TempDir(), "nacos-test-cache-dir")
	assert.NoError(t, os.MkdirAll(cacheDir, os.ModePerm))
	defer os.RemoveAll(cacheDir)

	r := &Registry{
		cfg: &Config{
			ServiceName: "test",
			CacheDir:    "/tmp/",
			Port:        "8088",
			AddressList: "127.0.0.1:8081",
		},
	}
	assert.NoError(t, r.Init())
	assert.True(t, r.initStatus.Load())
	assert.False(t, r.startStatus.Load())
	assert.NoError(t, r.Start())

	t.Run("return empty", func(t *testing.T) {
		ctrl := gomock.NewController(t)
		nameclient := mocks.NewMockINamingClient(ctrl)
		r.client = nameclient
		protoList = []string{"GRPC"}
		nameclient.EXPECT().SelectInstances(vo.SelectInstancesParam{
			Clusters:    []string{"test"},
			ServiceName: fmt.Sprintf("%v-%v", "eventmesh-server", "GRPC"),
			GroupName:   "1",
			HealthyOnly: true,
		}).Return([]model.Instance{}, nil).AnyTimes()
		val, err := r.FindEventMeshInfoByCluster("test")
		assert.NoError(t, err)
		assert.Equal(t, len(val), 0)
	})

	t.Run("return 1 instance", func(t *testing.T) {
		ctrl := gomock.NewController(t)
		nameclient := mocks.NewMockINamingClient(ctrl)
		r.client = nameclient
		protoList = []string{"GRPC"}
		nameclient.EXPECT().SelectInstances(vo.SelectInstancesParam{
			Clusters:    []string{"test"},
			ServiceName: fmt.Sprintf("%v-%v", "eventmesh-server", "GRPC"),
			GroupName:   "1",
			HealthyOnly: true,
		}).Return([]model.Instance{
			{
				InstanceId: "1",
			},
		}, nil).AnyTimes()
		val, err := r.FindEventMeshInfoByCluster("test")
		assert.NoError(t, err)
		assert.Equal(t, len(val), 1)
	})

	t.Run("return err", func(t *testing.T) {
		ctrl := gomock.NewController(t)
		nameclient := mocks.NewMockINamingClient(ctrl)
		r.client = nameclient
		protoList = []string{"GRPC"}
		mockErr := fmt.Errorf("mock err")
		nameclient.EXPECT().SelectInstances(vo.SelectInstancesParam{
			Clusters:    []string{"test"},
			ServiceName: fmt.Sprintf("%v-%v", "eventmesh-server", "GRPC"),
			GroupName:   "1",
			HealthyOnly: true,
		}).Return(nil, mockErr).AnyTimes()
		val, err := r.FindEventMeshInfoByCluster("test")
		assert.Error(t, err)
		assert.Equal(t, err, mockErr)
		assert.Nil(t, val)
	})
}

func Test_FindAllEventMeshInfo(t *testing.T) {
	cacheDir := filepath.Join(os.TempDir(), "nacos-test-cache-dir")
	assert.NoError(t, os.MkdirAll(cacheDir, os.ModePerm))
	defer os.RemoveAll(cacheDir)

	r := &Registry{
		cfg: &Config{
			ServiceName: "test",
			CacheDir:    "/tmp/",
			Port:        "8088",
			AddressList: "127.0.0.1:8081",
		},
	}
	assert.NoError(t, r.Init())
	assert.True(t, r.initStatus.Load())
	assert.False(t, r.startStatus.Load())
	assert.NoError(t, r.Start())

	t.Run("return empty", func(t *testing.T) {
		ctrl := gomock.NewController(t)
		nameclient := mocks.NewMockINamingClient(ctrl)
		r.client = nameclient
		protoList = []string{"GRPC"}
		nameclient.EXPECT().SelectInstances(vo.SelectInstancesParam{
			Clusters:    []string{},
			ServiceName: fmt.Sprintf("%v-%v", "eventmesh-server", "GRPC"),
			GroupName:   "GROUP",
			HealthyOnly: true,
		}).Return([]model.Instance{}, nil).AnyTimes()
		val, err := r.FindAllEventMeshInfo()
		assert.NoError(t, err)
		assert.Equal(t, len(val), 0)
	})

	t.Run("return 1 instance", func(t *testing.T) {
		ctrl := gomock.NewController(t)
		nameclient := mocks.NewMockINamingClient(ctrl)
		r.client = nameclient
		protoList = []string{"GRPC"}
		nameclient.EXPECT().SelectInstances(vo.SelectInstancesParam{
			Clusters:    []string{},
			ServiceName: fmt.Sprintf("%v-%v", "eventmesh-server", "GRPC"),
			GroupName:   "GROUP",
			HealthyOnly: true,
		}).Return([]model.Instance{
			{
				InstanceId: "1",
			},
		}, nil).AnyTimes()
		val, err := r.FindAllEventMeshInfo()
		assert.NoError(t, err)
		assert.Equal(t, len(val), 1)
	})

	t.Run("return err", func(t *testing.T) {
		ctrl := gomock.NewController(t)
		nameclient := mocks.NewMockINamingClient(ctrl)
		r.client = nameclient
		protoList = []string{"GRPC"}
		mockErr := fmt.Errorf("mock err")
		nameclient.EXPECT().SelectInstances(vo.SelectInstancesParam{
			Clusters:    []string{},
			ServiceName: fmt.Sprintf("%v-%v", "eventmesh-server", "GRPC"),
			GroupName:   "GROUP",
			HealthyOnly: true,
		}).Return(nil, mockErr).AnyTimes()
		val, err := r.FindAllEventMeshInfo()
		assert.Error(t, err)
		assert.Equal(t, err, mockErr)
		assert.Nil(t, val)
	})
}
