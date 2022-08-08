package selector

import (
	"errors"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/naming/selector"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/plugin"
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
