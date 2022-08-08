package selector

import (
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/naming/registry"
	nacos_registry "github.com/apache/incubator-eventmesh/eventmesh-workflow-go/plugin/naming/nacos/registry"
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
