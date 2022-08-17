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

package emserver

import (
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/config"
	"github.com/panjf2000/gnet/v2"
)

type TCPServer struct {
	tcpOption *config.TCPOption
	eng       gnet.Engine
	gnet.BuiltinEventEngine
}

func NewTCPServer(opt *config.TCPOption) (GracefulServer, error) {
	return &TCPServer{
		tcpOption: opt,
	}, nil
}

func (t *TCPServer) Serve() error {
	return gnet.Run(t, fmt.Sprintf("tcp://:%d", t.tcpOption.Port), gnet.WithMulticore(t.tcpOption.Multicore))
}

func (t *TCPServer) Stop() error {
	return nil
}

func (t *TCPServer) OnBoot(eng gnet.Engine) gnet.Action {
	return gnet.None
}

func (t *TCPServer) OnTraffic(c gnet.Conn) gnet.Action {
	return gnet.None
}
