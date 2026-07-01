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

package conf

import "testing"

func TestValidateDefaultConf(t *testing.T) {
	type args struct {
		cfg *GRPCConfig
	}
	tests := []struct {
		name    string
		args    args
		wantErr bool
	}{
		{
			name: "test no hosts",
			args: args{
				cfg: &GRPCConfig{},
			},
			wantErr: true,
		},
		{
			name: "test default duration",
			args: args{
				cfg: &GRPCConfig{
					Host: "1",
				},
			},
			wantErr: false,
		},
		{
			name: "test consumer enable, but no group",
			args: args{
				cfg: &GRPCConfig{
					Host: "1",
					ConsumerConfig: ConsumerConfig{
						Enabled: true,
					},
				},
			},
			wantErr: true,
		},
		{
			name: "test consumer enable, but no group",
			args: args{
				cfg: &GRPCConfig{
					Host: "1",
					ConsumerConfig: ConsumerConfig{
						Enabled:       true,
						ConsumerGroup: "test",
					},
				},
			},
			wantErr: false,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if err := ValidateDefaultConf(tt.args.cfg); (err != nil) != tt.wantErr {
				t.Errorf("ValidateDefaultConf() error = %v, wantErr %v", err, tt.wantErr)
			}
		})
	}
}
