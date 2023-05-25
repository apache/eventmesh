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
	"math/rand"
	"time"
)

const WeightRandomSelectorType = "weight_random"

func init() {
	rand.Seed(time.Now().UnixNano())
	registerSelectorBuilder(WeightRandomSelectorType, buildWeightRandomLoadSelector)
}

func buildWeightRandomLoadSelector(config *conf.EventMeshHttpClientConfig) (LoadBalanceSelector, error) {
	meshNodes, err := parseWeightedMeshNodeFromConfig(config)
	if err != nil {
		return nil, err
	}

	return NewWeightRandomLoadSelector(meshNodes)
}

type WeightRandomLoadSelector struct {
	clusterGroup    []MeshNode
	totalWeight     int64
	sameWeightGroup bool
}

func NewWeightRandomLoadSelector(clusterGroup []MeshNode) (*WeightRandomLoadSelector, error) {
	if len(clusterGroup) == 0 {
		return nil, errors.New("fail to create weight random load balancer selector, cluster group is empty")
	}
	var totalWeight int64 = 0
	firstWeight := clusterGroup[0].Weight
	sameWeightGroup := true
	for _, node := range clusterGroup {
		totalWeight += node.Weight
		if sameWeightGroup && firstWeight != node.Weight {
			sameWeightGroup = false
		}
	}
	return &WeightRandomLoadSelector{
		clusterGroup:    clusterGroup,
		totalWeight:     totalWeight,
		sameWeightGroup: sameWeightGroup,
	}, nil
}

func (s *WeightRandomLoadSelector) Select() MeshNode {
	if !s.sameWeightGroup {
		targetWeight := rand.Int63n(s.totalWeight)
		for _, node := range s.clusterGroup {
			targetWeight -= node.Weight
			if targetWeight < 0 {
				return node
			}
		}
	}
	return s.clusterGroup[rand.Intn(len(s.clusterGroup))]
}

func (s *WeightRandomLoadSelector) GetType() string {
	return WeightRandomSelectorType
}
