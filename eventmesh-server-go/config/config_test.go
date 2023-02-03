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

package config

import (
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin"
	testifyassert "github.com/stretchr/testify/assert"
	"gopkg.in/yaml.v3"
	"os"
	"testing"
	"time"
)

func TestConfig_Load(t *testing.T) {
	assert := testifyassert.New(t)

	config := &Config{}
	config.Common = &Common{
		RegistryName: "test",
		Cluster:      "test",
		Env:          "env",
		IDC:          "idc1",
	}
	config.Server.GRPCOption = &GRPCOption{
		Port: "10010",
		TLSOption: &TLSOption{
			EnableInsecure: false,
			CA:             "",
			Certfile:       "",
			Keyfile:        "",
		},
		SendPoolSize:          10,
		SubscribePoolSize:     10,
		RetryPoolSize:         10,
		PushMessagePoolSize:   10,
		ReplyPoolSize:         10,
		MsgReqNumPerSecond:    5,
		SessionExpiredInMills: 5 * time.Second,
		SendMessageTimeout:    5 * time.Second,
	}
	config.Server.HTTPOption = &HTTPOption{
		Port: "10010",
		TLSOption: &TLSOption{
			EnableInsecure: false,
			CA:             "",
			Certfile:       "",
			Keyfile:        "",
		},
	}
	config.Server.TCPOption = &TCPOption{
		Port: "10010",
		TLSOption: &TLSOption{
			EnableInsecure: false,
			CA:             "",
			Certfile:       "",
			Keyfile:        "",
		},
		Multicore: false,
	}
	config.ActivePlugins = map[string]string{
		"registry":  "nacos",
		"connector": "rocketmq",
		"log":       "default",
	}
	config.PProf = &PProfOption{
		Enable: true,
		Port:   "10011",
	}
	config.Plugins = plugin.Config{}

	configYAML := &Config{}
	contentYAML, err := os.ReadFile("./testdata/test_config.yaml")
	assert.NoError(err)
	if err := yaml.Unmarshal(contentYAML, &configYAML); err != nil {
		t.Fatal(err)
	}
	configYAML.Plugins = plugin.Config{}

	assert.EqualValues(configYAML, config)
}
