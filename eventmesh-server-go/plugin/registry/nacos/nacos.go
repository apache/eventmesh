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

package nacos

import (
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/registry"
	"github.com/gogf/gf/util/gconv"
	"github.com/nacos-group/nacos-sdk-go/v2/clients"
	"github.com/nacos-group/nacos-sdk-go/v2/clients/naming_client"
	"github.com/nacos-group/nacos-sdk-go/v2/common/constant"
	"github.com/nacos-group/nacos-sdk-go/v2/vo"
	"go.uber.org/atomic"
	"net"
	"strconv"
	"strings"
	"sync"
	"time"
)

var (
	defaultConnectTimeout         = 5000 * time.Millisecond
	DefaultClusterName            = "DEFAULT"
	DefaultGroupName              = "DEFAULT_GROUP"
	DefaultWeight         float64 = 10
	protoList                     = []string{"TCP", "HTTP", "GRPC"}
)

func init() {
	plugin.Register("nacos", &Registry{})
}

type Registry struct {
	initStatus    *atomic.Bool
	startStatus   *atomic.Bool
	cfg           *Config
	client        naming_client.INamingClient
	registryInfos *sync.Map // map[string]*registry.EventMeshRegisterInfo
}

func (r *Registry) Type() string {
	return registry.Type
}

func (r *Registry) Setup(name string, dec plugin.Decoder) error {
	conf := &Config{}
	if err := dec.Decode(conf); err != nil {
		return err
	}
	r.cfg = conf
	return nil
}

func (r *Registry) Init() error {
	r.initStatus = atomic.NewBool(true)
	r.startStatus = atomic.NewBool(false)
	r.registryInfos = new(sync.Map)
	return nil
}

func (r *Registry) Start() error {
	var p vo.NacosClientParam
	var addresses []string

	if len(r.cfg.AddressList) > 0 {
		addresses = strings.Split(r.cfg.AddressList, ",")
	}

	for _, address := range addresses {
		ip, port, err := net.SplitHostPort(address)
		if err != nil {
			return err
		}
		p.ServerConfigs = append(p.ServerConfigs, constant.ServerConfig{IpAddr: ip, Port: gconv.Uint64(port)})
	}

	p.ClientConfig = &constant.ClientConfig{
		TimeoutMs: uint64(defaultConnectTimeout.Milliseconds()),
		CacheDir:  r.cfg.CacheDir,
	}
	cli, err := clients.NewNamingClient(p)
	if err != nil {
		return err
	}

	r.startStatus.Store(true)
	r.client = cli
	return nil
}

func (r *Registry) Shutdown() error {
	r.startStatus.Store(false)
	r.initStatus.Store(false)
	r.client.CloseClient()
	return nil
}

func (r *Registry) FindEventMeshInfoByCluster(clusterName string) ([]*registry.EventMeshDataInfo, error) {
	var infos []*registry.EventMeshDataInfo
	meshServerName := config.GlobalConfig().Common.Name
	cluster := config.GlobalConfig().Common.Cluster
	for _, proto := range protoList {
		ins, err := r.client.SelectInstances(vo.SelectInstancesParam{
			Clusters:    []string{clusterName},
			ServiceName: fmt.Sprintf("%v-%v", meshServerName, proto),
			GroupName:   cluster,
			HealthyOnly: true,
		})
		if err != nil {
			return nil, err
		}
		if len(ins) == 0 {
			continue
		}
		for _, in := range ins {
			infos = append(infos, &registry.EventMeshDataInfo{
				EventMeshClusterName: in.ClusterName,
				EventMeshName:        in.ServiceName,
				Endpoint:             fmt.Sprintf("%v:%v", in.Ip, in.Port),
				LastUpdateTimestamp:  time.Time{},
				Metadata:             in.Metadata,
			})
		}
	}

	return infos, nil
}

func (r *Registry) FindAllEventMeshInfo() ([]*registry.EventMeshDataInfo, error) {
	var infos []*registry.EventMeshDataInfo
	meshServerName := config.GlobalConfig().Common.Name

	for _, proto := range protoList {
		ins, err := r.client.SelectInstances(vo.SelectInstancesParam{
			Clusters:    []string{},
			ServiceName: fmt.Sprintf("%v-%v", meshServerName, proto),
			GroupName:   "GROUP",
			HealthyOnly: true,
		})
		if err != nil {
			return nil, err
		}
		if len(ins) == 0 {
			continue
		}
		for _, in := range ins {
			infos = append(infos, &registry.EventMeshDataInfo{
				EventMeshClusterName: in.ClusterName,
				EventMeshName:        in.ServiceName,
				Endpoint:             fmt.Sprintf("%v:%v", in.Ip, in.Port),
				LastUpdateTimestamp:  time.Time{},
				Metadata:             in.Metadata,
			})
		}
	}

	return infos, nil
}

// FindEventMeshClientDistributionData not used
// deprecate
func (r *Registry) FindEventMeshClientDistributionData(clusterName, group, purpose string) (map[string]map[string]int, error) {
	return nil, nil
}

func (r *Registry) RegisterMetadata(map[string]string) {
	r.registryInfos.Range(func(key, value any) bool {
		r.Register(value.(*registry.EventMeshRegisterInfo))
		return true
	})
}

func (r *Registry) Register(info *registry.EventMeshRegisterInfo) error {
	ipPort := strings.Split(info.EndPoint, ":")
	if len(ipPort) != 2 {
		return fmt.Errorf("endpoint format err")
	}
	ip := ipPort[0]
	port, err := strconv.ParseInt(ipPort[1], 10, 64)
	if err != nil {
		return err
	}

	_, err = r.client.RegisterInstance(vo.RegisterInstanceParam{
		Ip:          ip,
		Port:        uint64(port),
		ServiceName: info.EventMeshName,
		GroupName:   uniqGroupName(info.ProtocolType),
		Healthy:     true,
		Enable:      true,
		Weight:      DefaultWeight,
	})
	if err != nil {
		return err
	}
	r.registryInfos.Store(info.EventMeshName, info)
	return nil
}

func (r *Registry) UnRegister(info *registry.EventMeshUnRegisterInfo) error {
	ipPort := strings.Split(info.EndPoint, ":")
	if len(ipPort) != 2 {
		return fmt.Errorf("endpoint format err")
	}
	ip := ipPort[0]
	port, err := strconv.ParseInt(ipPort[1], 10, 64)
	if err != nil {
		return err
	}

	_, err = r.client.DeregisterInstance(vo.DeregisterInstanceParam{
		Ip:          ip,
		Port:        uint64(port),
		ServiceName: info.EventMeshName,
		GroupName:   uniqGroupName(info.ProtocolType),
	})
	if err != nil {
		return err
	}
	r.registryInfos.Delete(info.EventMeshName)
	return nil
}

func uniqGroupName(protoType string) string {
	return fmt.Sprintf("%s-GROUP", protoType)
}
