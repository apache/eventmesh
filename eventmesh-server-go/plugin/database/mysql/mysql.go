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

package mysql

import (
	"github.com/apache/incubator-eventmesh/eventmesh-go/plugin"
)

const (
	PluginType = "database"
	PluginName = "mysql"
)

const (
	defaultDriverName = "mysql"
)

// PluginConfig global mysql plugin configuration
var PluginConfig Config

func init() {
	plugin.Register(PluginName, &MysqlPlugin{})
}

// Config mysql proxy config
type Config struct {
	DSN         string `yaml:"dsn"`
	MaxIdle     int    `yaml:"max_idle"`
	MaxOpen     int    `yaml:"max_open"`
	MaxLifetime int    `yaml:"max_lifetime"`
	DriverName  string `yaml:"driver_name"`
}

func (c *Config) setDefault() {
	if c.DriverName == "" {
		c.DriverName = defaultDriverName
	}
}

// MysqlPlugin load mysql plugin configuration
type MysqlPlugin struct{}

// Type plugin type
func (m *MysqlPlugin) Type() string {
	return PluginType
}

// Setup setup config mysql connect configuration (integration with gorm)
func (m *MysqlPlugin) Setup(name string, configDesc plugin.Decoder) (err error) {
	if err = configDesc.Decode(&PluginConfig); err != nil {
		return
	}
	PluginConfig.setDefault()
	return nil
}
