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

package util

import (
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/version"
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestBuildMeshClientID(t *testing.T) {
	type args struct {
		group   string
		cluster string
	}
	tests := []struct {
		name string
		args args
		want string
	}{
		{
			name: "build client id",
			args: args{
				group:   "test-group",
				cluster: "idc",
			},
			want: "test-group-(idc)-" + version.Current + "-" + PID(),
		},
		{
			name: "build client id with upper",
			args: args{
				group:   "Test-group",
				cluster: "IDC",
			},
			want: "Test-group-(IDC)-" + version.Current + "-" + PID(),
		},
		{
			name: "build client id wit space",
			args: args{
				group:   " Test-group   ",
				cluster: "idc ",
			},
			want: "Test-group-(idc)-" + version.Current + "-" + PID(),
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := BuildMeshClientID(tt.args.group, tt.args.cluster); got != tt.want {
				t.Errorf("BuildMeshClientID() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestBuildMeshTcpClientID(t *testing.T) {
	type args struct {
		sys     string
		purpose string
		cluster string
	}
	tests := []struct {
		name string
		args args
		want string
	}{
		{
			name: "uniq  mesh tcp client id",
			args: args{
				sys:     "1234",
				purpose: "test",
				cluster: "c1",
			},
			want: "1234-test-c1-" + version.Current + "-" + PID(),
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equalf(t, tt.want, BuildMeshTcpClientID(tt.args.sys, tt.args.purpose, tt.args.cluster), "BuildMeshTcpClientID(%v, %v, %v)", tt.args.sys, tt.args.purpose, tt.args.cluster)
		})
	}
}
