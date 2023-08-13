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

import (
	"github.com/apache/eventmesh/eventmesh-sdk-go/http/conf"
	"math/rand"
	"time"
)

const RandomSelectorType = "random"

func init() {
	rand.Seed(time.Now().UnixNano())
	registerSelectorBuilder(RandomSelectorType, buildRandomLoadSelector)
}

func buildRandomLoadSelector(config *conf.EventMeshHttpClientConfig) (LoadBalanceSelector, error) {
	meshNodes, err := parseMeshNodeFromConfig(config)
	if err != nil {
		return nil, err
	}
	return &RandomLoadSelector{clusterGroup: meshNodes}, nil
}

type RandomLoadSelector struct {
	clusterGroup []MeshNode
}

func (s *RandomLoadSelector) Select() MeshNode {
	return s.clusterGroup[rand.Intn(len(s.clusterGroup))]
}

func (s *RandomLoadSelector) GetType() string {
	return RandomSelectorType
}
