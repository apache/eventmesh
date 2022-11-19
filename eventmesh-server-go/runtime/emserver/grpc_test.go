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

package emserver

import (
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/config"
	"github.com/stretchr/testify/assert"
	"gopkg.in/yaml.v3"
	"testing"
	"time"
)

func Test_NewGRPCServer(t *testing.T) {
	staticCfg := `server:
  grpc:
    port: 10010
    tls:
      enable-secure: false
      ca: ""
      certfile: ""
      keyfile: ""
    pprof:
      port: 10011
    send-pool-size: 10
    subscribe-pool-size: 10
    retry-pool-size: 10
    push-message-pool-size: 10
    reply-pool-size: 10
    msg-req-num-per-second: 5
    cluster: "test"
    idc: "idc1"
    session-expired-in-mills: 5s
    send-message-timeout: 5s`
	cfg := &config.Config{}
	err := yaml.Unmarshal([]byte(staticCfg), cfg)
	assert.NoError(t, err)
	config.SetGlobalConfig(cfg)

	t.Run("create plain server", func(t *testing.T) {
		svr, err := NewGRPCServer(cfg.Server.GRPCOption)
		assert.NoError(t, err)
		assert.NotNil(t, svr)
	})

	t.Run("boot grpc srever", func(t *testing.T) {
		svr, err := NewGRPCServer(cfg.Server.GRPCOption)
		assert.NoError(t, err)
		assert.NotNil(t, svr)
		go func() {
			assert.NoError(t, svr.Serve())
		}()
		time.Sleep(3 * time.Second)
		svr.Stop()
	})
}
