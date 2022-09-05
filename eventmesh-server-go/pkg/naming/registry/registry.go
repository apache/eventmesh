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

// Package registry registers servers. A server report itself on start.
package registry

import (
	"errors"
	"sync"
)

// RegistryPluginType type for registry
var RegistryPluginType = "RegistryPluginType"

// ErrNotImplement is the not implemented error.
var ErrNotImplement = errors.New("not implement")

// DefaultRegistry is the default registry.
var DefaultRegistry Registry = &NoopRegistry{}

// SetDefaultRegistry sets the default registry.
func SetDefaultRegistry(r Registry) {
	DefaultRegistry = r
}

// Registry is the interface that defines a register.
type Registry interface {
	Register(service string) error
	Deregister(service string) error
}

var (
	registries = make(map[string]Registry)
	lock       = sync.RWMutex{}
)

// Register registers a named registry. Each service has its own registry.
func Register(name string, s Registry) {
	lock.Lock()
	registries[name] = s
	lock.Unlock()
}

// Get gets a named registry.
func Get(name string) Registry {
	lock.RLock()
	r := registries[name]
	lock.RUnlock()
	return r
}

// NoopRegistry is the noop registry.
type NoopRegistry struct{}

// Register always returns ErrNotImplement.
func (noop *NoopRegistry) Register(service string) error {
	return ErrNotImplement
}

// Deregister always return ErrNotImplement.
func (noop *NoopRegistry) Deregister(service string) error {
	return ErrNotImplement
}
