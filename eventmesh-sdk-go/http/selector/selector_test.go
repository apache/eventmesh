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
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestCreateNewSelector(t *testing.T) {
	eventMeshClientConfig := conf.DefaultEventMeshHttpClientConfig
	eventMeshClientConfig.SetLiteEventMeshAddr("127.0.0.1:10105;127.0.0.1:10106")
	selector, err := CreateNewSelector(eventMeshClientConfig.GetLoadBalanceType(), &eventMeshClientConfig)
	if err != nil {
		t.Fail()
	}
	assert.Equal(t, RandomSelectorType, selector.GetType())
}

func TestCreateNewSelectorWithWeight(t *testing.T) {
	eventMeshClientConfig := conf.DefaultEventMeshHttpClientConfig
	eventMeshClientConfig.SetLiteEventMeshAddr("127.0.0.1:10105:1;127.0.0.1:10106:2")
	eventMeshClientConfig.SetLoadBalanceType(WeightRoundRobinSelectorType)
	selector, err := CreateNewSelector(eventMeshClientConfig.GetLoadBalanceType(), &eventMeshClientConfig)
	if err != nil {
		t.Fail()
	}
	assert.Equal(t, WeightRoundRobinSelectorType, selector.GetType())
	assert.Equal(t, int64(3), selector.(*WeightRoundRobinLoadSelector).totalWeight)
	assert.Equal(t, int64(1), selector.(*WeightRoundRobinLoadSelector).clusterGroup[0].Weight)
}
