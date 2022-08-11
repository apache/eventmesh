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

// This file shows how to load plugins through a yaml config file.

package plugin

import (
	"errors"
	"fmt"
	"time"

	yaml "gopkg.in/yaml.v3"
)

var (
	// SetupTimeout is the timeout for initialization of each plugin.
	// Modify it if some plugins' initialization does take a long time.
	SetupTimeout = 3 * time.Second

	// MaxPluginSize is the max number of plugins.
	MaxPluginSize = 1000
)

// Config is the configuration of all plugins. plugin type => { plugin name => plugin config }
type Config map[string]map[string]yaml.Node

// Setup loads plugins by configuration.
func (c Config) Setup() error {
	// load plugins one by one through the config file and put them into an ordered plugin queue.
	plugins, status, err := c.loadPlugins()
	if err != nil {
		return err
	}

	// remove and setup plugins one by one from the front of the ordered plugin queue.
	if _, err := c.setupPlugins(plugins, status); err != nil {
		return err
	}
	return nil
}

func (c Config) loadPlugins() (chan pluginInfo, map[string]bool, error) {
	var (
		plugins = make(chan pluginInfo, MaxPluginSize) // use channel as plugin queue
		// plugins' status. plugin key => {true: init done, false: init not done}.
		status = make(map[string]bool)
	)
	for typ, factories := range c {
		for name, cfg := range factories {
			factory := Get(typ, name)
			if factory == nil {
				return nil, nil, fmt.Errorf("plugin %s:%s no registered or imported, do not configure", typ, name)
			}
			p := pluginInfo{
				factory: factory,
				typ:     typ,
				name:    name,
				cfg:     cfg,
			}
			select {
			case plugins <- p:
			default:
				return nil, nil, fmt.Errorf("plugin number exceed max limit:%d", len(plugins))
			}
			status[p.key()] = false
		}
	}
	return plugins, status, nil
}

func (c Config) setupPlugins(plugins chan pluginInfo, status map[string]bool) ([]pluginInfo, error) {
	var (
		result []pluginInfo
		num    = len(plugins)
	)
	for num > 0 {
		for i := 0; i < num; i++ {
			p := <-plugins
			if err := p.setup(); err != nil {
				return nil, err
			}
			status[p.key()] = true
			result = append(result, p)
		}
		if len(plugins) == num { // none plugin is setup, circular dependency exists.
			return nil, fmt.Errorf("cycle depends, not plugin is setup")
		}
		num = len(plugins) // continue to process plugins that were moved to tail of the channel.
	}
	return result, nil
}

// ------------------------------------------------------------------------------------- //

// pluginInfo is the information of a plugin.
type pluginInfo struct {
	factory Plugin
	typ     string
	name    string
	cfg     yaml.Node
}

// setup initializes a single plugin.
func (p *pluginInfo) setup() error {
	var (
		ch  = make(chan struct{})
		err error
	)
	go func() {
		err = p.factory.Setup(p.name, &YamlNodeDecoder{Node: &p.cfg})
		close(ch)
	}()
	select {
	case <-ch:
	case <-time.After(SetupTimeout):
		return fmt.Errorf("setup plugin %s timeout", p.key())
	}
	if err != nil {
		return fmt.Errorf("setup plugin %s error: %v", p.key(), err)
	}
	return nil
}

// YamlNodeDecoder is a decoder for a yaml.Node of the yaml config file.
type YamlNodeDecoder struct {
	Node *yaml.Node
}

// Decode decodes a yaml.Node of the yaml config file.
func (d *YamlNodeDecoder) Decode(cfg interface{}) error {
	if d.Node == nil {
		return errors.New("yaml node empty")
	}
	return d.Node.Decode(cfg)
}

// key returns the unique index of plugin in the format of 'type-name'.
func (p *pluginInfo) key() string {
	return p.typ + "-" + p.name
}
