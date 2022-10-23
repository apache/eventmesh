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
	"time"

	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/common/protocol/grpc"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/connector"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/protocol"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/consts"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
)

var (
	ErrProtocolPluginNotFound = fmt.Errorf("protocol plugin not found")
)

// SubscribeProcessor Subscribe process subscribe message
func SubscribeProcessor(ctx context.Context, gctx *GRPCContext, emiter *EventEmitter, msg *pb.Subscription) error {
	hdr := msg.Header
	if err := ValidateHeader(hdr); err != nil {
		log.Warnf("invalid header:%v", err)
		emiter.sendStreamResp(hdr, grpc.EVENTMESH_PROTOCOL_HEADER_ERR)
		return err
	}
	if err := ValidateSubscription(WEBHOOK, msg); err != nil {
		log.Warnf("invalid body:%v", err)
		emiter.sendStreamResp(hdr, grpc.EVENTMESH_PROTOCOL_BODY_ERR)
		return err
	}

	return nil
}

func SubscribeStreamProcessor(ctx context.Context, gctx *GRPCContext, emiter *EventEmitter, msg *pb.Subscription) error {
	hdr := msg.Header
	if err := ValidateHeader(hdr); err != nil {
		log.Warnf("invalid header:%v", err)
		emiter.sendStreamResp(hdr, grpc.EVENTMESH_PROTOCOL_HEADER_ERR)
		return err
	}
	if err := ValidateSubscription(WEBHOOK, msg); err != nil {
		log.Warnf("invalid body:%v", err)
		emiter.sendStreamResp(hdr, grpc.EVENTMESH_PROTOCOL_BODY_ERR)
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
			Emiter:           emiter,
		})
	}
	for _, cli := range clients {
		if err := cmgr.RegisterClient(cli); err != nil {
			return err
		}
	}
	meshConsumer, err := cmgr.GetConsumer(consumerGroup)
	if err != nil {
		return err
	}
	requireRestart := false
	for _, cli := range clients {
		if meshConsumer.RegisterClient(cli) {
			requireRestart = true
		}
	}
	if requireRestart {
		log.Infof("ConsumerGroup %v topic info changed, restart EventMesh Consumer", consumerGroup)
		return cmgr.restartConsumer(consumerGroup)
	} else {
		log.Warnf("EventMesh consumer [%v] didn't restart.", consumerGroup)
	}

	return nil
}

// AsyncMessageProcessor process async message
func AsyncMessageProcessor(ctx context.Context, gctx *GRPCContext, emiter *EventEmitter, msg *pb.SimpleMessage) error {
	hdr := msg.Header
	if err := ValidateHeader(hdr); err != nil {
		log.Warnf("invalid header:%v", err)
		emiter.sendStreamResp(hdr, grpc.EVENTMESH_PROTOCOL_HEADER_ERR)
		return err
	}
	if err := ValidateMessage(msg); err != nil {
		log.Warnf("invalid body:%v", err)
		emiter.sendStreamResp(hdr, grpc.EVENTMESH_PROTOCOL_BODY_ERR)
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
		log.Warnf("protocol plugin not found:%v", protocolType)
		emiter.sendStreamResp(hdr, grpc.EVENTMESH_Plugin_NotFound_ERR)
		return ErrProtocolPluginNotFound
	}
	cevt, err := adp.ToCloudEvent(&grpc.SimpleMessageWrapper{SimpleMessage: msg})
	if err != nil {
		emiter.sendStreamResp(hdr, grpc.EVENTMESH_Transfer_Protocol_ERR)
		return err
	}
	ep, err := gctx.ProducerMgr.GetProducer(pg)
	if err != nil {
		return err
	}
	return ep.Send(
		SendMessageContext{
			Ctx:         ctx,
			Event:       cevt,
			BizSeqNO:    seqNum,
			ProducerAPI: ep,
			CreateTime:  time.Now(),
		},
		&connector.SendCallback{
			OnSuccess: func(result *connector.SendResult) {
				emiter.sendStreamResp(hdr, grpc.SUCCESS)
				log.Infof("message|eventMesh2mq|REQ|ASYNC|send2MQCost=%vms|topic=%v|bizSeqNo=%v|uniqueId=%v",
					time.Now().Sub(start).Milliseconds(), topic, seqNum, uid)
			},
			OnError: func(result *connector.ErrorResult) {
				emiter.sendStreamResp(hdr, grpc.EVENTMESH_SEND_ASYNC_MSG_ERR)
				log.Errorf("message|eventMesh2mq|REQ|ASYNC|send2MQCost=%vms|topic=%v|bizSeqNo=%v|uniqueId=%v, err:%v",
					time.Now().Sub(start).Milliseconds(), topic, seqNum, uid, result.Err)
			},
		},
	)
}

func ReplyMessageProcessor(ctx context.Context, gctx *GRPCContext, emiter *EventEmitter, msg *pb.SimpleMessage) error {
	hdr := msg.Header
	if err := ValidateHeader(hdr); err != nil {
		log.Warnf("invalid header:%v", err)
		emiter.sendStreamResp(hdr, grpc.EVENTMESH_PROTOCOL_HEADER_ERR)
		return err
	}
	if err := ValidateMessage(msg); err != nil {
		log.Warnf("invalid body:%v", err)
		emiter.sendStreamResp(hdr, grpc.EVENTMESH_PROTOCOL_BODY_ERR)
		return err
	}
	seqNum := msg.SeqNum
	uniqID := msg.UniqueId
	producerGroup := msg.ProducerGroup
	mqCluster := defaultIfEmpty(msg.Properties[consts.PROPERTY_MESSAGE_CLUSTER], "defaultCluster")
	replyTopic := mqCluster + "_" + consts.RR_REPLY_TOPIC
	msg.Topic = replyTopic
	protocolType := hdr.ProtocolType
	adp := plugin.Get(plugin.Protocol, protocolType).(protocol.Adapter)
	if adp == nil {
		log.Warnf("protocol plugin not found:%v", protocolType)
		emiter.sendStreamResp(hdr, grpc.EVENTMESH_Plugin_NotFound_ERR)
		return ErrProtocolPluginNotFound
	}
	cevt, err := adp.ToCloudEvent(&grpc.SimpleMessageWrapper{SimpleMessage: msg})
	if err != nil {
		log.Warnf("transfer to cloud event msg err:%v", err)
		emiter.sendStreamResp(hdr, grpc.EVENTMESH_Transfer_Protocol_ERR)
		return err
	}
	emProducer, err := gctx.ProducerMgr.GetProducer(producerGroup)
	if err != nil {
		log.Warnf("no eventmesh producer found, err:%v, group:%v", err, producerGroup)
		emiter.sendStreamResp(hdr, grpc.EVENTMESH_Producer_Group_NotFound_ERR)
		return err
	}
	start := time.Now()
	return emProducer.Reply(
		SendMessageContext{
			Ctx:         ctx,
			Event:       cevt,
			BizSeqNO:    seqNum,
			ProducerAPI: emProducer,
			CreateTime:  time.Now(),
		},
		&connector.SendCallback{
			OnSuccess: func(result *connector.SendResult) {
				log.Infof("message|mq2eventmesh|REPLY|ReplyToServer|send2MQCost=%vms|topic=%v|bizSeqNo=%v|uniqueId=%v",
					time.Now().Sub(start).Milliseconds(), replyTopic, seqNum, uniqID)
			},
			OnError: func(result *connector.ErrorResult) {
				emiter.sendStreamResp(hdr, grpc.EVENTMESH_REPLY_MSG_ERR)
				log.Warnf("message|mq2eventmesh|REPLY|ReplyToServer|send2MQCost=%vms|topic=%v|bizSeqNo=%v|uniqueId=%v",
					time.Now().Sub(start).Milliseconds(), replyTopic, seqNum, uniqID, result.Err)
			},
		},
	)
}

func defaultIfEmpty(in interface{}, def string) string {
	if in == nil {
		return def
	}
	return in.(string)
}
