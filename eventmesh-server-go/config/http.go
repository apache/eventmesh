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

package config

// HTTPOption option for http/https server
type HTTPOption struct {
	// Port http server listen
	Port string `yaml:"port" toml:"port"`

	// TLSOption process with the tls configuration
	*TLSOption `yaml:"tls" toml:"tls"`

	// PProfOption if pprof is enabled, server
	// will start on given port, and you can check
	// on http://ip:port/pprof/debug
	*PProfOption `yaml:"pprof" toml:"pprof"`
}
