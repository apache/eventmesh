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

	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin"
	"gopkg.in/yaml.v3"
)

// ServerConfigPath is the file path of server config file.
var ServerConfigPath = defaultConfigPath

const (
	defaultConfigPath = "./configs/eventmesh-server.yaml"
)

type Config struct {
	Name   string `yaml:"name" toml:"name"`
	Server struct {
		*HTTPOption `yaml:"http" toml:"http"`
		*GRPCOption `yaml:"grpc" toml:"grpc"`
		*TCPOption  `yaml:"tcp" toml:"tcp"`
	}
	ActivePlugins map[string]string `yaml:"active-plugins" toml:"active-plugins"`
	Plugins       plugin.Config     `yaml:"plugins,omitempty"`
}

var globalConfig atomic.Value

func init() {
	globalConfig.Store(defaultConfig())
}

func defaultConfig() *Config {
	cfg := &Config{}
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
