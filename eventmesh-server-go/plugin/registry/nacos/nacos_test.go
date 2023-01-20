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
	"github.com/golang/mock/gomock"
	"github.com/nacos-group/nacos-sdk-go/v2/mock"
	"github.com/stretchr/testify/assert"
	"os"
	"path/filepath"
	"testing"
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
		mock.NewMockINamingClient(ctrl)
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
	//ctrl := gomock.NewController(t)
	//nameclient := mock.NewMockINamingClient(ctrl)
	assert.NoError(t, r.Start())
}
