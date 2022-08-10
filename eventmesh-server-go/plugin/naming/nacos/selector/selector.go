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
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-go/internal/naming/registry"
	nacos_registry "github.com/apache/incubator-eventmesh/eventmesh-go/plugin/naming/nacos/registry"
	"github.com/gogf/gf/util/gconv"
	"github.com/nacos-group/nacos-sdk-go/v2/clients/naming_client"
	"github.com/nacos-group/nacos-sdk-go/v2/vo"
	"net"
	"strconv"
)

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
