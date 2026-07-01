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
	"fmt"
	"github.com/apache/eventmesh/eventmesh-sdk-go/http/conf"
	"regexp"
	"strconv"
	"strings"
)

var (
	ipPortRegexp       = regexp.MustCompile(`(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}):(\d{4,5})`)
	ipPortWeightRegexp = regexp.MustCompile(`(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}):(\d{4,5}):(\d{1,6})`)
)

type selectorBuilder func(*conf.EventMeshHttpClientConfig) (LoadBalanceSelector, error)

var selectorBuilderMap = make(map[string]selectorBuilder)

type LoadBalanceSelector interface {
	Select() MeshNode
	GetType() string
}

type MeshNode struct {
	Addr   string
	Weight int64
}

func GetSelectorBuilder(selectorName string) (selectorBuilder, error) {
	if builder, ok := selectorBuilderMap[selectorName]; ok {
		return builder, nil
	}
	return nil, fmt.Errorf("unknow loadbalancer type %s", selectorName)
}

func CreateNewSelector(selectorName string, config *conf.EventMeshHttpClientConfig) (LoadBalanceSelector, error) {
	builder, err := GetSelectorBuilder(selectorName)
	if err != nil {
		return nil, err
	}
	return builder(config)
}

func registerSelectorBuilder(selectorName string, builder selectorBuilder) {
	selectorBuilderMap[selectorName] = builder
}

func parseMeshNodeFromConfig(config *conf.EventMeshHttpClientConfig) ([]MeshNode, error) {
	if config == nil || len(config.LiteEventMeshAddr()) == 0 {
		return nil, errors.New("fail in loading load balancer, invalid mesh address")
	}

	addrList := strings.Split(strings.TrimSpace(config.LiteEventMeshAddr()), ";")
	if len(addrList) == 0 {
		return nil, errors.New("fail in loading load balancer, invalid mesh address")
	}

	result := make([]MeshNode, 0, len(addrList))
	for _, addr := range addrList {
		if len(addr) == 0 {
			continue
		}
		isMatch := ipPortRegexp.MatchString(strings.TrimSpace(addr))
		if !isMatch {
			return nil, fmt.Errorf("fail in loading load balancer, invalid mesh address : %s", addr)
		}
		result = append(result, MeshNode{
			Addr: addr,
		})
	}

	return result, nil
}

func parseWeightedMeshNodeFromConfig(config *conf.EventMeshHttpClientConfig) ([]MeshNode, error) {
	if config == nil || len(config.LiteEventMeshAddr()) == 0 {
		return nil, errors.New("fail in loading load balancer, invalid mesh address")
	}
	addrList := strings.Split(strings.TrimSpace(config.LiteEventMeshAddr()), ";")
	if len(addrList) == 0 {
		return nil, errors.New("fail in loading load balancer, invalid mesh address")
	}

	result := make([]MeshNode, 0, len(addrList))
	for _, addr := range addrList {
		if len(addr) == 0 {
			continue
		}
		matches := ipPortWeightRegexp.FindStringSubmatch(strings.TrimSpace(addr))
		if len(matches) != 4 {
			return nil, fmt.Errorf("fail in loading load balancer, invalid mesh address : %s", addr)
		}
		weight, _ := strconv.ParseInt(matches[3], 10, 64)
		result = append(result, MeshNode{
			Addr:   fmt.Sprintf("%s:%s", matches[1], matches[2]),
			Weight: weight,
		})
	}
	return result, nil
}
