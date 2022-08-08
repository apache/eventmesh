package mysql

import (
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/plugin"
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
