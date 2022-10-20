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
	"context"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/common/protocol/grpc"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
	"time"
)

// Subscribe process subscribe message
func Subscribe(ctx context.Context, gctx *GRPCContext, msg *pb.Subscription) error {
	hdr := msg.Header
	if err := ValidateHeader(hdr); err != nil {
		log.Warnf("invalid header:%v", err)
		gctx.SendResp(grpc.EVENTMESH_PROTOCOL_HEADER_ERR)
		return err
	}
	if err := ValidateSubscription(WEBHOOK, msg); err != nil {
		log.Warnf("invalid body:%v", err)
		gctx.SendResp(grpc.EVENTMESH_PROTOCOL_BODY_ERR)
		return err
	}

	return nil
}

func SubscribeStream(ctx context.Context, gctx *GRPCContext, stream pb.ConsumerService_SubscribeStreamServer, msg *pb.Subscription) error {
	hdr := msg.Header
	if err := ValidateHeader(hdr); err != nil {
		log.Warnf("invalid header:%v", err)
		gctx.SendResp(grpc.EVENTMESH_PROTOCOL_HEADER_ERR)
		return err
	}
	if err := ValidateSubscription(WEBHOOK, msg); err != nil {
		log.Warnf("invalid body:%v", err)
		gctx.SendResp(grpc.EVENTMESH_PROTOCOL_BODY_ERR)
		return err
	}
	cmgr := gctx.ConsumerMgr
	consumerGroup := msg.ConsumerGroup
	var clients []*GroupClient
	for _, item := range msg.SubscriptionItems {
		clients = append(clients, &GroupClient{
			ENV:              hdr.Env,
			IDC:              hdr.Idc,
			SYS:              hdr.Sys,
			IP:               hdr.Ip,
			PID:              hdr.Pid,
			ConsumerGroup:    consumerGroup,
			Topic:            item.Topic,
			SubscriptionMode: item.Mode,
			GRPCType:         STREAM,
			LastUPTime:       time.Now(),
		})
	}
	return nil
}
