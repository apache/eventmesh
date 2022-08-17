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

package main

import (
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/runtime"
)

func main() {
	cfg, err := config.LoadConfig(config.ServerConfigPath)
	if err != nil {
		log.Fatalf("load config err:%v", err)
	}
	if err := SetupPlugins(cfg); err != nil {
		log.Fatalf("setup plugin err:%v", err)
	}
	config.SetGlobalConfig(cfg)

	if err := runtime.Start(); err != nil {
		log.Fatalf("start runtime server err:%v", err)
	}

	log.Infof("stop runtime server success")
}

// SetupPlugins registers client config and setups plugins according to the Config.
func SetupPlugins(cfg *config.Config) error {
	// SetupConfig all plugins
	if cfg.Plugins != nil {
		if err := cfg.Plugins.Setup(); err != nil {
			return err
		}
	}
	return nil
}
