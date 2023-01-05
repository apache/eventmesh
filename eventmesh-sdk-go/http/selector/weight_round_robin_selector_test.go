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
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/http/conf"
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestWeightRoundRobinLoadSelector_Select(t *testing.T) {
	testAddr := "192.168.0.1:10105:10;192.168.0.2:10105:20;192.168.0.3:10105:30;"
	eventMeshClientConfig := conf.DefaultEventMeshHttpClientConfig
	eventMeshClientConfig.SetLoadBalanceType(WeightRoundRobinSelectorType)
	eventMeshClientConfig.SetLiteEventMeshAddr(testAddr)

	selector, err := CreateNewSelector(eventMeshClientConfig.GetLoadBalanceType(), &eventMeshClientConfig)
	if err != nil {
		t.Fail()
	}

	counter := make(map[string]int)
	testRange := 1000
	for i := 0; i < testRange; i++ {
		node := selector.Select()
		counter[node.Addr]++
	}

	assert.True(t, counter["192.168.0.2:10105"] > counter["192.168.0.1:10105"])
	assert.True(t, counter["192.168.0.3:10105"] > counter["192.168.0.2:10105"])
}

func TestWeightRoundRobinLoadSelector_GetType(t *testing.T) {
	testAddr := "192.168.0.1:10105:10;192.168.0.2:10105:20;192.168.0.3:10105:30;"
	eventMeshClientConfig := conf.DefaultEventMeshHttpClientConfig
	eventMeshClientConfig.SetLoadBalanceType(WeightRoundRobinSelectorType)
	eventMeshClientConfig.SetLiteEventMeshAddr(testAddr)
	selector, err := CreateNewSelector(eventMeshClientConfig.GetLoadBalanceType(), &eventMeshClientConfig)
	if err != nil {
		t.Fail()
	}
	assert.Equal(t, WeightRoundRobinSelectorType, selector.GetType())
}
