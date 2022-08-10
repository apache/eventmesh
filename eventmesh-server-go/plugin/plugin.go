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

// Package plugin implements a general plugin factory system which provides plugin registration and loading.
// It is mainly used when certain plugins must be loaded by configuration.
// This system is not supposed to register plugins that do not rely on configuration like codec. Instead, plugins
// that do not rely on configuration should be registered by calling methods in certain packages.
package plugin

var plugins = make(map[string]map[string]Factory) // plugin type => { plugin name => plugin factory }

// Factory is the interface for plugin factory abstraction.
// Custom Plugins need to implement this interface to be registered as a plugin with certain type.
type Factory interface {
	// Type returns type of the plugin, i.e. selector, log, config, tracing.
	Type() string
	// Setup loads plugin by configuration.
	// The data structure of the configuration of the plugin needs to be defined in advance。
	Setup(name string, dec Decoder) error
}

// Decoder is the interface used to decode plugin configuration.
type Decoder interface {
	Decode(cfg interface{}) error // the input param is the custom configuration of the plugin
}

// Register registers a plugin factory.
// Name of the plugin should be specified.
// It is supported to register instances which are the same implementation of plugin Factory
// but use different configuration.
func Register(name string, f Factory) {
	factories, ok := plugins[f.Type()]
	if !ok {
		factories = make(map[string]Factory)
		plugins[f.Type()] = factories
	}
	factories[name] = f
}

// Get returns a plugin Factory by its type and name.
func Get(typ string, name string) Factory {
	return plugins[typ][name]
}
