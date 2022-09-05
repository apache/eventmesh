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

package registry

import (
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/naming/registry"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/util"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin"
	"github.com/gogf/gf/util/gconv"
	"github.com/nacos-group/nacos-sdk-go/v2/clients"
	"github.com/nacos-group/nacos-sdk-go/v2/clients/naming_client"
	"github.com/nacos-group/nacos-sdk-go/v2/common/constant"
	"github.com/nacos-group/nacos-sdk-go/v2/vo"
	"github.com/pkg/errors"
	"net"
	"strconv"
	"strings"
)

const (
	DefaultClusterName = "DEFAULT"
	DefaultGroupName   = "DEFAULT_GROUP"
)

const (
	defaultConnectTimeout = 5000
	defaultWeight         = 100
)

func init() {
	plugin.Register("nacos", &Registry{})
}

// Registry register service
type Registry struct {
	Provider naming_client.INamingClient
	cfg      *Config
	host     string
	port     int
}

// newRegistry 新建实例
func newRegistry(provider naming_client.INamingClient, cfg *Config) *Registry {
	return &Registry{
		Provider: provider,
		cfg:      cfg,
	}
}

// Type return registry type
func (r *Registry) Type() string {
	return "registry"
}

// Setup setup config
func (r *Registry) Setup(name string, configDec plugin.Decoder) error {
	if configDec == nil {
		return errors.New("registry config decoder empty")
	}
	conf := &PluginConfig{}
	if err := configDec.Decode(conf); err != nil {
		return err
	}
	return r.register(conf)
}

// Register registry service, application can invoke this method to register service to remote registry-server
func (r *Registry) Register(_ string) error {
	host, portRaw, err := net.SplitHostPort(r.cfg.Address)
	if err != nil {
		return err
	}
	port, err := strconv.ParseInt(portRaw, 10, 64)
	if err != nil {
		return err
	}
	r.host = host
	r.port = gconv.Int(port)
	if r.cfg.Weight == 0 {
		r.cfg.Weight = defaultWeight
	}
	var req = vo.RegisterInstanceParam{
		Ip:          r.host,
		Port:        uint64(r.port),
		ServiceName: r.cfg.ServiceName,
		GroupName:   DefaultGroupName,
		Healthy:     true,
		Enable:      true,
		Weight:      gconv.Float64(r.cfg.Weight),
	}
	result, err := r.Provider.RegisterInstance(req)
	if err != nil {
		return errors.Wrap(err, "fail to Register instance")
	}
	if !result {
		return errors.New("fail to Register instance")
	}
	return nil
}

// Deregister de-registry service, application can invoke this method to de-register service to remote registry-server
func (r *Registry) Deregister(_ string) error {
	var req = vo.DeregisterInstanceParam{
		Ip:          r.host,
		Port:        uint64(r.port),
		ServiceName: r.cfg.ServiceName,
		GroupName:   DefaultGroupName,
	}
	result, err := r.Provider.DeregisterInstance(req)
	if err != nil {
		return errors.Wrap(err, "fail to Deregister instance")
	}
	if !result {
		return errors.New("fail to Deregister instance")
	}
	return nil
}

func (r *Registry) register(conf *PluginConfig) error {
	provider, err := r.newProvider(conf)
	if err != nil {
		return err
	}
	ip, err := util.GetIP()
	if err != nil {
		return err
	}
	serverName := config.GlobalConfig().Name
	cfg := &Config{
		ServiceName: serverName,
		Address:     fmt.Sprintf("%s:%d", ip, config.GlobalConfig().Server.Port),
	}
	registry.Register(serverName, newRegistry(provider, cfg))
	return nil
}

func (r *Registry) newProvider(cfg *PluginConfig) (naming_client.INamingClient, error) {
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
