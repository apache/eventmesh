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

func TestRandomLoadSelector_Select(t *testing.T) {
	testAddr := "192.168.0.1:10105;192.168.0.2:10105;192.168.0.3:10105;"
	eventMeshClientConfig := conf.DefaultEventMeshHttpClientConfig
	eventMeshClientConfig.SetLoadBalanceType(RandomSelectorType)
	eventMeshClientConfig.SetLiteEventMeshAddr(testAddr)

	selector, err := CreateNewSelector(eventMeshClientConfig.GetLoadBalanceType(), &eventMeshClientConfig)
	if err != nil {
		t.Fail()
	}

	for i := 0; i < 100; i++ {
		node := selector.Select()
		if node.Addr != "192.168.0.1:10105" && node.Addr != "192.168.0.2:10105" && node.Addr != "192.168.0.3:10105" {
			t.Fail()
		}
	}
}

func TestRandomLoadSelector_GetType(t *testing.T) {
	eventMeshClientConfig := conf.DefaultEventMeshHttpClientConfig
	eventMeshClientConfig.SetLoadBalanceType(RandomSelectorType)
	selector, err := CreateNewSelector(eventMeshClientConfig.GetLoadBalanceType(), &eventMeshClientConfig)
	if err != nil {
		t.Fail()
	}
	assert.Equal(t, RandomSelectorType, selector.GetType())
}
