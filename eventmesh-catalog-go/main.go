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

package main

import (
	"github.com/apache/incubator-eventmesh/eventmesh-catalog-go/api"
	"github.com/apache/incubator-eventmesh/eventmesh-catalog-go/api/proto"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	_ "github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/naming/registry"
	_ "github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/database/mysql"
	_ "github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/naming/nacos/selector"
)

func main() {
	s, err := NewServer()
	if err != nil {
		log.Fatal("flow new server fail: " + err.Error())
	}
	router(s)
	if err = s.Run(); err != nil {
		log.Fatal("run server fail: " + err.Error())
	}
}

func router(s *Server) {
	proto.RegisterCatalogServer(s.Server, api.NewCatalogImpl())
}
