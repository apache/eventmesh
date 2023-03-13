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

package consumer

import (
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/util"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/protocol/grpc/validator"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
	"github.com/stretchr/testify/assert"
	"testing"
)

func Test_processor_Subscribe(t *testing.T) {
	createHeader := func() *pb.RequestHeader {
		return &pb.RequestHeader{
			Env:             "env",
			Region:          "sh",
			Idc:             "Idc",
			Ip:              util.GetIP(),
			Pid:             util.GetIP(),
			Sys:             "Sys",
			Username:        "em",
			Password:        "pw",
			Language:        "go",
			ProtocolType:    "cloudeevents",
			ProtocolVersion: "v1",
			ProtocolDesc:    "for mock",
		}
	}
	tests := []struct {
		name   string
		expect func(t *testing.T)
	}{
		{
			name: "header no idc err",
			expect: func(t *testing.T) {
				p := &processor{}
				hdr := createHeader()
				hdr.Idc = ""
				_, err := p.Subscribe(nil, &pb.Subscription{Header: hdr})
				assert.Equal(t, err, validator.ErrHeaderNoIDC)
			},
		},
		{
			name: "header no ip err",
			expect: func(t *testing.T) {
				p := &processor{}
				hdr := createHeader()
				hdr.Ip = ""
				_, err := p.Subscribe(nil, &pb.Subscription{Header: hdr})
				assert.Equal(t, err, validator.ErrHeaderNoIP)
			},
		},
		{
			name: "header no env err",
			expect: func(t *testing.T) {
				p := &processor{}
				hdr := createHeader()
				hdr.Env = ""
				_, err := p.Subscribe(nil, &pb.Subscription{Header: hdr})
				assert.Equal(t, err, validator.ErrHeaderNoENV)
			},
		},
		{
			name: "header no pid err",
			expect: func(t *testing.T) {
				p := &processor{}
				hdr := createHeader()
				hdr.Pid = ""
				_, err := p.Subscribe(nil, &pb.Subscription{Header: hdr})
				assert.Equal(t, err, validator.ErrHeaderNoPID)
			},
		},
		{
			name: "header no sys err",
			expect: func(t *testing.T) {
				p := &processor{}
				hdr := createHeader()
				hdr.Sys = ""
				_, err := p.Subscribe(nil, &pb.Subscription{Header: hdr})
				assert.Equal(t, err, validator.ErrHeaderNoSYS)
			},
		},
		{
			name: "header no username err",
			expect: func(t *testing.T) {
				p := &processor{}
				hdr := createHeader()
				hdr.Username = ""
				_, err := p.Subscribe(nil, &pb.Subscription{Header: hdr})
				assert.Equal(t, err, validator.ErrHeaderNoUser)
			},
		},
		{
			name: "header no passwd err",
			expect: func(t *testing.T) {
				p := &processor{}
				hdr := createHeader()
				hdr.Password = ""
				_, err := p.Subscribe(nil, &pb.Subscription{Header: hdr})
				assert.Equal(t, err, validator.ErrHeaderNoPASSWD)
			},
		},
		{
			name: "header no language err",
			expect: func(t *testing.T) {
				p := &processor{}
				hdr := createHeader()
				hdr.Language = ""
				_, err := p.Subscribe(nil, &pb.Subscription{Header: hdr})
				assert.Equal(t, err, validator.ErrHeaderNoLANG)
			},
		},
		{
			name: "webhook no url",
			expect: func(t *testing.T) {
				p := &processor{}
				hdr := createHeader()
				_, err := p.Subscribe(nil, &pb.Subscription{Header: hdr, Url: ""})
				assert.Equal(t, err, validator.ErrSubscriptionNoURL)
			},
		},
		{
			name: "stream no topic",
			expect: func(t *testing.T) {
				p := &processor{}
				hdr := createHeader()
				_, err := p.Subscribe(nil, &pb.Subscription{
					Header: hdr,
					Url:    "http://mock.com",
				})
				assert.Equal(t, err, validator.ErrSubscriptionNoItem)
			},
		},
		{
			name: "register client err",
			expect: func(t *testing.T) {
				mockMgr, _ := NewConsumerManager()
				hdr := createHeader()
				req := &pb.Subscription{
					Header: hdr,
					Url:    "http://mock.com",
					SubscriptionItems: []*pb.Subscription_SubscriptionItem{
						{
							Topic: "test-topic",
							Mode:  pb.Subscription_SubscriptionItem_CLUSTERING,
							Type:  pb.Subscription_SubscriptionItem_ASYNC,
						},
					},
				}
				p := &processor{}
				resp, err := p.Subscribe(mockMgr, req)
				assert.NoError(t, err)
				t.Log(resp.String())
			},
		},
	}

	err := config.GlobalConfig().Plugins.Setup()
	assert.NoError(t, err)
	plugin.SetActivePlugin(config.GlobalConfig().ActivePlugins)
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			tc.expect(t)
		})
	}
}

func Test_processor_Unsubscribe(t *testing.T) {

}
