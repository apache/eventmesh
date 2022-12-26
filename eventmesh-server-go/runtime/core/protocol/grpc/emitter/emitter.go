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

package emitter

import (
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/common/protocol/grpc"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
)

//go:generate mockgen -destination ./mocks/emitter.go -package mocks github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/protocol/grpc/emitter EventEmitter
type EventEmitter interface {
	SendStreamResp(hdr *pb.RequestHeader, code *grpc.StatusCode) error
}

type eventEmitter struct {
	emitter pb.ConsumerService_SubscribeStreamServer
}

func NewEventEmitter(stream pb.ConsumerService_SubscribeStreamServer) EventEmitter {
	return &eventEmitter{
		emitter: stream,
	}
}

func (e *eventEmitter) SendStreamResp(hdr *pb.RequestHeader, code *grpc.StatusCode) error {
	return e.emitter.Send(&pb.SimpleMessage{
		Header:  hdr,
		Content: code.ToJSONString(),
	})
}
