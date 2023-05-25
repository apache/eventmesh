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
	"errors"
	"github.com/apache/eventmesh/eventmesh-sdk-go/http/conf"
	"go.uber.org/atomic"
)

const WeightRoundRobinSelectorType = "weight_round_robin"

func init() {
	registerSelectorBuilder(WeightRoundRobinSelectorType, buildWeightRoundRobinLoadSelector)
}

func buildWeightRoundRobinLoadSelector(config *conf.EventMeshHttpClientConfig) (LoadBalanceSelector, error) {
	meshNodes, err := parseWeightedMeshNodeFromConfig(config)
	if err != nil {
		return nil, err
	}

	return NewWeightRoundRobinLoadSelector(meshNodes)
}

type WeightRoundRobinMeshNode struct {
	MeshNode
	currentWeight *atomic.Int64
}

type WeightRoundRobinLoadSelector struct {
	clusterGroup []WeightRoundRobinMeshNode
	totalWeight  int64
}

func NewWeightRoundRobinLoadSelector(clusterGroup []MeshNode) (*WeightRoundRobinLoadSelector, error) {
	if len(clusterGroup) == 0 {
		return nil, errors.New("fail to create weight round robin load balancer selector, cluster group is empty")
	}
	var totalWeight int64 = 0
	weightRoundRobinClusterGroup := make([]WeightRoundRobinMeshNode, 0, len(clusterGroup))
	for _, node := range clusterGroup {
		totalWeight += node.Weight
		weightRoundRobinClusterGroup = append(weightRoundRobinClusterGroup, WeightRoundRobinMeshNode{
			MeshNode:      node,
			currentWeight: atomic.NewInt64(0),
		})
	}

	return &WeightRoundRobinLoadSelector{
		clusterGroup: weightRoundRobinClusterGroup,
		totalWeight:  totalWeight,
	}, nil
}

func (s *WeightRoundRobinLoadSelector) Select() MeshNode {
	var targetWeight *WeightRoundRobinMeshNode = nil
	for i := range s.clusterGroup {
		node := &s.clusterGroup[i]
		node.currentWeight.Add(node.Weight)
		if targetWeight == nil || targetWeight.currentWeight.Load() < node.currentWeight.Load() {
			targetWeight = node
		}
	}
	targetWeight.currentWeight.Sub(s.totalWeight)
	return targetWeight.MeshNode
}

func (s *WeightRoundRobinLoadSelector) GetType() string {
	return WeightRoundRobinSelectorType
}
