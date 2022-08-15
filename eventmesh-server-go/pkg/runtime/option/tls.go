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

package option

// TLSOption option to tls
type TLSOption struct {
	// EnableInsecure enable the insecure request
	EnableInsecure bool `yaml:"enable-secure" toml:"enable-secure"`

	// CA no client authentication is used,
	// and the file CA is used to verify the server certificate
	CA string `yaml:"ca" toml:"ca"`

	//  Certfile client authentication is used with the specified cert/key pair.
	// The server certificate is verified with the system CAs
	Certfile string `yaml:"certfile" toml:"certfile"`

	//  client authentication is used with the specified cert/key pair.
	// The server certificate is verified using the specified CA file
	Keyfile string `yaml:"keyfile" toml:"keyfile"`
}
