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

package selector

import (
	"errors"
	"github.com/apache/incubator-eventmesh/eventmesh-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-go/internal/naming/selector"
	"github.com/apache/incubator-eventmesh/eventmesh-go/plugin"
	"github.com/gogf/gf/util/gconv"
	"github.com/nacos-group/nacos-sdk-go/v2/clients"
	"github.com/nacos-group/nacos-sdk-go/v2/clients/naming_client"
	"github.com/nacos-group/nacos-sdk-go/v2/common/constant"
	"github.com/nacos-group/nacos-sdk-go/v2/vo"
	"net"
	"strings"
)

const (
	defaultConnectTimeout = 5000
)

// FactoryConfig define registry factory config
type FactoryConfig struct {
	AddressList string `yaml:"address_list"`
}

func init() {
	plugin.Register("nacos", &SelectorFactory{})
}

// SelectorFactory naming-selector factory
type SelectorFactory struct {
}

// Type return registry type
func (f *SelectorFactory) Type() string {
	return "selector"
}

// Setup setup config
func (f *SelectorFactory) Setup(name string, configDec plugin.Decoder) error {
	if configDec == nil {
		return errors.New("selector config decoder empty")
	}
	conf := &FactoryConfig{}
	if err := configDec.Decode(conf); err != nil {
		return err
	}
	client, err := newClient(conf)
	if err != nil {
		return err
	}
	selector.Register(config.GlobalConfig().Server.Name, newSelector(client))
	return nil
}

func newClient(cfg *FactoryConfig) (naming_client.INamingClient, error) {
	var p vo.NacosClientParam
	var addresses []string
	if len(cfg.AddressList) > 0 {
		addresses = strings.Split(cfg.AddressList, ",")
	}
	for _, address := range addresses {
		ip, port, err := net.SplitHostPort(address)
		if err != nil {
			return nil, err
		}
		p.ServerConfigs = append(p.ServerConfigs, constant.ServerConfig{IpAddr: ip, Port: gconv.Uint64(port)})
	}
	p.ClientConfig = &constant.ClientConfig{TimeoutMs: defaultConnectTimeout}
	provider, err := clients.NewNamingClient(p)
	if err != nil {
		return nil, err
	}
	return provider, nil
}
