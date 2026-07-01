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

package tcp

type UserAgent struct {
	Env       string `json:"env"`
	Subsystem string `json:"subsystem"`
	Path      string `json:"path"`
	Pid       int    `json:"pid"`
	Host      string `json:"host"`
	Port      int    `json:"port"`
	Version   string `json:"version"`
	Username  string `json:"username"`
	Password  string `json:"password"`
	Idc       string `json:"idc"`
	Group     string `json:"group"`
	Purpose   string `json:"purpose"`
	Unack     int    `json:"unack"`
}

func NewUserAgent(env string, subsystem string, path string, pid int, host string, port int, version string,
	username string, password string, idc string, producerGroup string, consumerGroup string) *UserAgent {
	return &UserAgent{Env: env, Subsystem: subsystem, Path: path, Pid: pid, Host: host, Port: port, Version: version,
		Username: username, Password: password, Idc: idc, Group: producerGroup}
}
