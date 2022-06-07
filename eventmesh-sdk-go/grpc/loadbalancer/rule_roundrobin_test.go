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
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/conf"
	"github.com/stretchr/testify/assert"
	"go.uber.org/atomic"
	"testing"
)

func TestRoundRobinRule_Choose(t *testing.T) {
	type fields struct {
		BaseRule     BaseRule
		cycleCounter *atomic.Int64
	}
	type args struct {
		in0 interface{}
	}
	fid := fields{
		BaseRule: func() BaseRule {
			lb, _ := NewLoadBalancer(conf.RoundRobin, []*StatusServer{
				{
					RealServer:      "127.0.0.1",
					ReadyForService: true,
					Host:            "127.0.0.1",
				},
				{
					RealServer:      "127.0.0.2",
					ReadyForService: true,
					Host:            "127.0.0.2",
				},
				{
					RealServer:      "127.0.0.3",
					ReadyForService: true,
					Host:            "127.0.0.3",
				},
			})
			return BaseRule{
				lb: lb,
			}
		}(),
		cycleCounter: atomic.NewInt64(-1),
	}
	tests := []struct {
		name    string
		fields  fields
		args    args
		want    interface{}
		wantErr assert.ErrorAssertionFunc
	}{
		{
			name:   "round1 always be first",
			fields: fid,
			args: args{
				in0: "",
			},
			want: "127.0.0.1",
			wantErr: func(t assert.TestingT, err error, i ...interface{}) bool {
				return true
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			r := &RoundRobinRule{
				BaseRule:     tt.fields.BaseRule,
				cycleCounter: tt.fields.cycleCounter,
			}
			for _, v := range []string{"127.0.0.1", "127.0.0.2", "127.0.0.3", "127.0.0.1", "127.0.0.2"} {
				got, err := r.Choose(tt.args.in0)
				if !tt.wantErr(t, err, fmt.Sprintf("Choose(%v)", tt.args.in0)) {
					return
				}
				assert.Equalf(t, v, got.(*StatusServer).Host, "Choose(%v)", tt.args.in0)
			}
		})
	}
}
