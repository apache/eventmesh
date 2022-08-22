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
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/naming/registry"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/naming/selector"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin"
	nacos_registry "github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/naming/nacos/registry"
	"github.com/gogf/gf/util/gconv"
	"github.com/nacos-group/nacos-sdk-go/v2/clients"
	"github.com/nacos-group/nacos-sdk-go/v2/clients/naming_client"
	"github.com/nacos-group/nacos-sdk-go/v2/common/constant"
	"github.com/nacos-group/nacos-sdk-go/v2/vo"
	"net"
	"strconv"
	"strings"
)

const (
	defaultConnectTimeout = 5000
)

func init() {
	plugin.Register("nacos", &Selector{})
}

// Selector 路由选择器
type Selector struct {
	Client naming_client.INamingClient
}

// newRegistry 新建实例
func newSelector(client naming_client.INamingClient) *Selector {
	return &Selector{
		Client: client,
	}
}

// Type return registry type
func (s *Selector) Type() string {
	return "selector"
}

// Setup setup config
func (s *Selector) Setup(name string, configDec plugin.Decoder) error {
	if configDec == nil {
		return errors.New("selector config decoder empty")
	}
	conf := &PluginConfig{}
	if err := configDec.Decode(conf); err != nil {
		return err
	}
	client, err := s.newClient(conf)
	if err != nil {
		return err
	}
	selector.Register(config.GlobalConfig().Name, newSelector(client))
	return nil
}

// Select 选择服务节点
func (s *Selector) Select(serviceName string) (*registry.Instance, error) {
	instanceReq := vo.SelectOneHealthInstanceParam{
		ServiceName: serviceName,
		GroupName:   nacos_registry.DefaultGroupName,
		Clusters:    []string{nacos_registry.DefaultClusterName},
	}
	instance, err := s.Client.SelectOneHealthyInstance(instanceReq)
	if err != nil {
		return nil, fmt.Errorf("get one instance err: %s", err.Error())
	}
	if instance == nil {
		return nil, fmt.Errorf("get one instance return empty")
	}
	return &registry.Instance{
		Address:     net.JoinHostPort(instance.Ip, strconv.Itoa(int(instance.Port))),
		ServiceName: serviceName,
		Weight:      gconv.Int(instance.Weight),
		Clusters:    instance.ClusterName,
		Metadata:    instance.Metadata,
	}, nil
}

func (s *Selector) newClient(cfg *PluginConfig) (naming_client.INamingClient, error) {
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
