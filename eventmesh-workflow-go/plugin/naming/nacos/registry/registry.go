package registry

import (
	"github.com/gogf/gf/util/gconv"
	"github.com/nacos-group/nacos-sdk-go/v2/clients/naming_client"
	"github.com/nacos-group/nacos-sdk-go/v2/vo"
	"github.com/pkg/errors"
	"net"
	"strconv"
)

const (
	DefaultClusterName = "DEFAULT"
	DefaultGroupName   = "DEFAULT_GROUP"
)
const (
	defaultWeight = 100
)

// Registry register service
type Registry struct {
	// Provider: public for user custom
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
	r.port = int(port)
	if r.cfg.Weight == 0 {
		r.cfg.Weight = defaultWeight
	}
	return r.register()
}

func (r *Registry) register() error {
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
