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
	"gopkg.in/yaml.v3"
	"os"
)

var (
	cfg *Config
)

type Config struct {
	Flow struct {
		Queue struct {
			Store string `yaml:"store"`
			Topic string `yaml:"topic"`
		} `yaml:"queue"`
		Scheduler struct {
			Type     string `yaml:"type"`
			Interval int    `yaml:"interval"`
		} `yaml:"scheduler"`
		Selector string `yaml:"selector"`
		Protocol string `yaml:"protocol"`
	} `yaml:"flow"`
	Catalog struct {
		ServerName string `yaml:"server_name"`
	} `yaml:"catalog"`
	EventMesh struct {
		Host string `yaml:"host"`
		Env  string `yaml:"env"`
		IDC  string `yaml:"idc"`
		GRPC struct {
			Port int `yaml:"port"`
		} `yaml:"grpc"`
		Sys           string `yaml:"sys"`
		UserName      string `yaml:"username"`
		Password      string `yaml:"password"`
		ProducerGroup string `yaml:"producer_group"`
		ConsumerGroup string `yaml:"consumer_group"`
		TTL           int    `yaml:"ttl"`
	} `yaml:"eventmesh"`
	Metrics struct {
		EndpointPort string `yaml:"endpoint_port"`
	} `yaml:"metrics"`
}

// Setup setup config
func Setup(path string) error {
	var err error
	cfg, err = parseConfigFromFile(path)
	if err != nil {
		return err
	}
	return nil
}

// Get get config
func Get() *Config {
	return cfg
}

func parseConfigFromFile(configPath string) (*Config, error) {
	buf, err := os.ReadFile(configPath)
	if err != nil {
		return nil, err
	}
	c := &Config{}
	if err = yaml.Unmarshal(buf, c); err != nil {
		return nil, err
	}
	return c, nil
}
