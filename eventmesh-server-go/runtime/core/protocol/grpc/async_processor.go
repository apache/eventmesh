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
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/common/protocol/grpc"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/connector"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/protocol"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/protocol/grpc/producer"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
	"time"
)

var (
	ErrProtocolPluginNotFound = fmt.Errorf("protocol plugin not found")
)

// AsyncMessage process async message
func AsyncMessage(ctx context.Context, gctx *GRPCContext, msg *pb.SimpleMessage) error {
	hdr := msg.Header
	if err := ValidateHeader(hdr); err != nil {
		log.Warnf("invalid header:%v", err)
		gctx.SendResp(grpc.EVENTMESH_PROTOCOL_HEADER_ERR)
		return err
	}
	if err := ValidateMessage(msg); err != nil {
		log.Warnf("invalid body:%v", err)
		gctx.SendResp(grpc.EVENTMESH_PROTOCOL_BODY_ERR)
		return err
	}

	// TODO no ack check, add rate limiter
	seqNum := msg.SeqNum
	uid := msg.UniqueId
	topic := msg.Topic
	pg := msg.ProducerGroup
	start := time.Now()
	protocolType := hdr.ProtocolType
	adp := plugin.Get(plugin.Protocol, protocolType).(protocol.Adapter)
	if adp == nil {
		return ErrProtocolPluginNotFound
	}
	cevt, err := adp.ToCloudEvent(&grpc.SimpleMessageWrapper{SimpleMessage: msg})
	if err != nil {
		return err
	}
	ep, err := gctx.ProducerMgr.GetProducer(pg)
	if err != nil {
		return err
	}
	return ep.Send(
		producer.SendMessageContext{
			Ctx:         ctx,
			Event:       cevt,
			BizSeqNO:    seqNum,
			ProducerAPI: ep,
			CreateTime:  time.Now(),
		},
		&connector.SendCallback{
			OnSuccess: func(result *connector.SendResult) {
				gctx.SendResp(grpc.SUCCESS)
				log.Infof("message|eventMesh2mq|REQ|ASYNC|send2MQCost=%vms|topic=%v|bizSeqNo=%v|uniqueId=%v",
					time.Now().Sub(start).Milliseconds(), topic, seqNum, uid)
			},
			OnError: func(result *connector.ErrorResult) {
				gctx.SendResp(grpc.EVENTMESH_SEND_ASYNC_MSG_ERR)
				log.Errorf("message|eventMesh2mq|REQ|ASYNC|send2MQCost=%vms|topic=%v|bizSeqNo=%v|uniqueId=%v, err:%v",
					time.Now().Sub(start).Milliseconds(), topic, seqNum, uid, result.Err)
			},
		})
}
