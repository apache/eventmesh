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

import "github.com/apache/incubator-eventmesh/eventmesh-go/pkg/naming/registry"

// Selector is the interface that defines the selector.
type Selector interface {
	// Select gets a backend instance by service name.
	Select(serviceName string) (*registry.Instance, error)
}

var (
	selectors = make(map[string]Selector)
)

// Register registers a named Selector, such as l5, cmlb and tseer.
func Register(name string, s Selector) {
	selectors[name] = s
}

// Get gets a named Selector.
func Get(name string) Selector {
	s := selectors[name]
	return s
}

func unregisterForTesting(name string) {
	delete(selectors, name)
}
