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
	"io/ioutil"
	"sync/atomic"
	"time"

	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin"
	"gopkg.in/yaml.v3"
)

// ServerConfigPath is the file path of server config file.
var ServerConfigPath = defaultConfigPath

const (
	defaultConfigPath = "./configs/eventmesh-server.yaml"
)

type Config struct {
	Common *Common `yaml:"common" toml:"common"`
	Server struct {
		*HTTPOption `yaml:"http" toml:"http"`
		*GRPCOption `yaml:"grpc" toml:"grpc"`
		*TCPOption  `yaml:"tcp" toml:"tcp"`
	}
	PProf         *PProfOption      `yaml:"pprof" toml:"pprof"`
	ActivePlugins map[string]string `yaml:"active-plugins" toml:"active-plugins"`
	Plugins       plugin.Config     `yaml:"plugins,omitempty"`
}

var globalConfig atomic.Value

func init() {
	globalConfig.Store(defaultConfig())
}

func defaultConfig() *Config {
	cfg := &Config{}
	cfg.Common = &Common{
		Name:         "eventmesh-server",
		RegistryName: "eventmesh-go",
		Cluster:      "1",
		Env:          "{}",
		IDC:          "idc1",
	}
	cfg.Server.GRPCOption = &GRPCOption{
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
	cfg.Server.HTTPOption = &HTTPOption{
		Port: "10010",
		TLSOption: &TLSOption{
			EnableInsecure: false,
			CA:             "",
			Certfile:       "",
			Keyfile:        "",
		},
	}
	cfg.Server.TCPOption = &TCPOption{
		Port: "10010",
		TLSOption: &TLSOption{
			EnableInsecure: false,
			CA:             "",
			Certfile:       "",
			Keyfile:        "",
		},
		Multicore: false,
	}
	cfg.ActivePlugins = map[string]string{
		"connector": "standalone",
		"log":       "default",
	}
	cfg.PProf = &PProfOption{
		Enable: true,
		Port:   "10011",
	}
	cfg.Plugins = map[string]map[string]yaml.Node{
		"connector": {
			"standalone": yaml.Node{},
		},
	}
	return cfg
}

// GlobalConfig returns the global Config.
func GlobalConfig() *Config {
	return globalConfig.Load().(*Config)
}

// SetGlobalConfig set the global Config.
func SetGlobalConfig(cfg *Config) {
	globalConfig.Store(cfg)
}

// LoadGlobalConfig loads a Config from the config file path and sets it as the global Config.
func LoadGlobalConfig(configPath string) error {
	cfg, err := LoadConfig(configPath)
	if err != nil {
		return err
	}
	SetGlobalConfig(cfg)
	return nil
}

// LoadConfig loads a Config from the config file path.
func LoadConfig(configPath string) (*Config, error) {
	return parseConfigFromFile(configPath)
}

func parseConfigFromFile(configPath string) (*Config, error) {
	buf, err := ioutil.ReadFile(configPath)
	if err != nil {
		return nil, err
	}
	cfg := defaultConfig()
	if err := yaml.Unmarshal(buf, cfg); err != nil {
		return nil, err
	}
	return cfg, nil
}
