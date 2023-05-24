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
	"github.com/apache/eventmesh/eventmesh-sdk-go/grpc/proto"
	. "github.com/onsi/ginkgo"
	. "github.com/onsi/gomega"
	"github.com/stretchr/testify/assert"
	"sync"
)

var _ = Describe("dispatcher test", func() {

	Context("messageDispatcher_addHandler test ", func() {
		type fields struct {
			topicMap *sync.Map
			poolsize int
		}

		type args struct {
			topic string
			hdl   OnMessage
		}
		It("test add handler", func() {
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
							return nil
						},
					},
					wantErr: func(t assert.TestingT, err error, i ...interface{}) bool {
						return true
					},
				},
			}

			for _, tt := range tests {
				m := &messageDispatcher{
					topicMap: tt.fields.topicMap,
					poolsize: tt.fields.poolsize,
				}
				Ω(m.addHandler(tt.args.topic, tt.args.hdl)).To(BeNil())
			}
		})

	})
})
