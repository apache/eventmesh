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
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestStatusServer_String(t *testing.T) {
	type fields struct {
		ReadyForService bool
		Host            string
		RealServer      interface{}
	}
	tests := []struct {
		name   string
		fields fields
		want   string
	}{
		{
			name: "test string",
			fields: fields{
				RealServer:      "srv",
				ReadyForService: true,
				Host:            "127.0.0.1",
			},
			want: "",
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &StatusServer{
				ReadyForService: tt.fields.ReadyForService,
				Host:            tt.fields.Host,
				RealServer:      tt.fields.RealServer,
			}
			assert.NotEmpty(t, s.String(), "String()")
			t.Logf(s.String())
		})
	}
}
