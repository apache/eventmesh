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

package loadbalancer

import (
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/conf"
	"github.com/stretchr/testify/assert"
	"sync"
	"testing"
)

func TestNewLoadBalancer(t *testing.T) {
	type args struct {
		lbType conf.LoadBalancerType
		srvs   []*StatusServer
	}
	tests := []struct {
		name    string
		args    args
		want    LoadBalancer
		wantErr bool
	}{
		{
			name: "lb with random",
			args: args{
				srvs:   []*StatusServer{},
				lbType: conf.Random,
			},
			want:    nil,
			wantErr: false,
		},
		{
			name: "lb with roundrobin",
			args: args{
				srvs:   []*StatusServer{},
				lbType: conf.RoundRobin,
			},
			want:    nil,
			wantErr: false,
		},
		{
			name: "lb with iphash",
			args: args{
				srvs:   []*StatusServer{},
				lbType: conf.IPHash,
			},
			want:    nil,
			wantErr: false,
		},
		{
			name: "lb without type",
			args: args{
				srvs: []*StatusServer{},
			},
			want:    nil,
			wantErr: true,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			_, err := NewLoadBalancer(tt.args.lbType, tt.args.srvs)
			if (err != nil) != tt.wantErr {
				t.Errorf("NewLoadBalancer() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
		})
	}
}

func TestBaseLoadBalancer_AddServer(t *testing.T) {
	type fields struct {
		servers []*StatusServer
		lock    *sync.RWMutex
		rule    Rule
	}
	type args struct {
		srvs []*StatusServer
	}
	tests := []struct {
		name   string
		fields fields
		args   args
	}{
		{
			name: "add srv",
			fields: fields{
				servers: []*StatusServer{},
				lock:    new(sync.RWMutex),
				rule:    &RoundRobinRule{},
			},
			args: args{
				srvs: []*StatusServer{{
					RealServer:      "",
					Host:            "127.1.1.1",
					ReadyForService: false,
				}},
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			b := &BaseLoadBalancer{
				servers: tt.fields.servers,
				lock:    tt.fields.lock,
				rule:    tt.fields.rule,
			}
			b.AddServer(tt.args.srvs)
			assert.Equal(t, len(tt.args.srvs), len(b.GetAllStatusServer()))
		})
	}
}

func TestBaseLoadBalancer_GetAllStatusServer(t *testing.T) {
	type fields struct {
		servers []*StatusServer
		lock    *sync.RWMutex
		rule    Rule
	}
	tests := []struct {
		name   string
		fields fields
		want   int
	}{
		{
			name: "",
			fields: fields{
				servers: []*StatusServer{{
					RealServer:      "1",
					ReadyForService: true,
					Host:            "127.0.0.1",
				}},
				lock: new(sync.RWMutex),
				rule: &RoundRobinRule{},
			},
			want: 1,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			b := &BaseLoadBalancer{
				servers: tt.fields.servers,
				lock:    tt.fields.lock,
				rule:    tt.fields.rule,
			}
			assert.Equalf(t, tt.want, len(b.GetAllStatusServer()), "GetAllStatusServer()")
		})
	}
}

func TestBaseLoadBalancer_GetAvailableServer(t *testing.T) {
	type fields struct {
		servers []*StatusServer
		lock    *sync.RWMutex
		rule    Rule
	}
	tests := []struct {
		name   string
		fields fields
		want   int
	}{
		{
			name: "1 ok",
			fields: fields{
				servers: []*StatusServer{{
					RealServer:      "1",
					ReadyForService: true,
					Host:            "127.0.0.1",
				}, {
					RealServer:      "2",
					ReadyForService: false,
					Host:            "127.0.0.2",
				}},
				lock: new(sync.RWMutex),
				rule: &RoundRobinRule{},
			},
			want: 1,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			b := &BaseLoadBalancer{
				servers: tt.fields.servers,
				lock:    tt.fields.lock,
				rule:    tt.fields.rule,
			}
			assert.Equalf(t, tt.want, len(b.GetAvailableServer()), "GetAvailableServer()")
		})
	}
}
