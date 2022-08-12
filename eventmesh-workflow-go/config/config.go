package config

import (
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin"
)

type Config struct {
	Server struct {
		Port uint16 `yaml:"port"`
		Name string `yaml:"name"`
	}
	Flow struct {
		Queue struct {
			Store string `yaml:"store"`
		} `yaml:"queue"`
		Schedule struct {
			Interval int `yaml:"interval"`
		} `yaml:"schedule"`
	} `yaml:"flow"`
	Plugins plugin.Config `yaml:"plugins,omitempty"`
}
