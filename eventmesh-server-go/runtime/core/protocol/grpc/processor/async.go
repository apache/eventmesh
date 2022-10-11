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

package processor

import (
	"context"
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/common/protocol/grpc"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/connector"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/protocol"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/protocol/grpc/producer"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/protocol/grpc/service"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
	ce "github.com/cloudevents/sdk-go/v2"
	"strconv"
	"time"
)

var (
	ErrProtocolPluginNotFound = fmt.Errorf("protocol plugin not found")
)

type AsyncMessage struct {
}

func (a *AsyncMessage) ProcessAsyncMessage(ctx context.Context, gctx *service.GRPCContext, msg *pb.SimpleMessage) error {
	hdr := msg.Header
	if err := ValidateHeader(hdr); err != nil {
		return err
	}
	if err := ValidateMessage(msg); err != nil {
		return err
	}

	// TODO no ack check, add rate limiter
	seqNum := msg.SeqNum
	uid := msg.UniqueId
	topic := msg.Topic
	pg := msg.ProducerGroup
	ttl, err := strconv.ParseInt(msg.Ttl, 10, 32)
	if err != nil {
		return err
	}
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
	return ep.Request(
		producer.SendMessageContext{
			Ctx:         ctx,
			Event:       cevt,
			BizSeqNO:    seqNum,
			ProducerAPI: ep,
			CreateTime:  time.Now(),
		},
		&connector.RequestReplyCallback{
			OnSuccess: func(event *ce.Event) {
				m, err := adp.FromCloudEvent(event)
				if err != nil {
					log.Warnf("onsuccess, but serialize message err:%v", err)
					return
				}
				mp := m.(grpc.SimpleMessageWrapper)

				log.Info("")
			},
			OnError: func(result *connector.ErrorResult) {

			},
		}, time.Duration(ttl)*time.Millisecond)
}
