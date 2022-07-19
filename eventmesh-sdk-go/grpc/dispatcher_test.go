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

package grpc

import (
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/proto"
	"github.com/stretchr/testify/assert"
	"sync"
	"testing"
)

func Test_messageDispatcher_addHandler(t *testing.T) {
	type fields struct {
		topicMap *sync.Map
		poolsize int
	}
	type args struct {
		topic string
		hdl   OnMessage
	}
	tests := []struct {
		name    string
		fields  fields
		args    args
		wantErr assert.ErrorAssertionFunc
	}{
		{
			name: "test add handler",
			fields: fields{
				topicMap: new(sync.Map),
				poolsize: 5,
			},
			args: args{
				topic: "handler-1",
				hdl: func(message *proto.SimpleMessage) interface{} {
					t.Logf("handle message")
					return nil
				},
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			m := &messageDispatcher{
				topicMap: tt.fields.topicMap,
				poolsize: tt.fields.poolsize,
			}
			tt.wantErr(t, m.addHandler(tt.args.topic, tt.args.hdl), fmt.Sprintf("addHandler(%v, %v)", tt.args.topic, tt.args.hdl))
		})
	}
}
