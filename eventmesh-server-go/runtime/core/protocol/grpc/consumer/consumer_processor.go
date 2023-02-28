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
	"context"
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/common/protocol/grpc"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/connector"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/protocol"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/consts"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/protocol/grpc/emitter"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/protocol/grpc/producer"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/protocol/grpc/validator"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
	"time"
)

var (
	ErrProtocolPluginNotFound = fmt.Errorf("protocol plugin not found")
)

type Processor interface {
	Subscribe(consumerMgr ConsumerManager, msg *pb.Subscription) (*pb.Response, error)
	UnSubscribe(consumerMgr ConsumerManager, msg *pb.Subscription) (*pb.Response, error)
	SubscribeStream(consumerMgr ConsumerManager, emiter emitter.EventEmitter, msg *pb.Subscription) error
	Heartbeat(consumerMgr ConsumerManager, msg *pb.Heartbeat) (*pb.Response, error)
	ReplyMessage(ctx context.Context, producerMgr producer.ProducerManager, emiter emitter.EventEmitter, msg *pb.SimpleMessage) error
}

type processor struct {
}

func (p *processor) Subscribe(consumerMgr ConsumerManager, msg *pb.Subscription) (*pb.Response, error) {
	hdr := msg.Header
	if err := validator.ValidateHeader(hdr); err != nil {
		log.Warnf("invalid header:%v", err)
		return buildPBResponse(grpc.EVENTMESH_PROTOCOL_HEADER_ERR), err
	}
	if err := validator.ValidateSubscription(consts.WEBHOOK, msg); err != nil {
		log.Warnf("invalid body:%v", err)
		return buildPBResponse(grpc.EVENTMESH_PROTOCOL_BODY_ERR), err
	}
	consumerGroup := msg.ConsumerGroup
	url := msg.Url
	items := msg.SubscriptionItems
	var newClients []*GroupClient
	for _, item := range items {
		newClients = append(newClients, &GroupClient{
			ENV:              hdr.Env,
			IDC:              hdr.Idc,
			SYS:              hdr.Sys,
			IP:               hdr.Ip,
			PID:              hdr.Pid,
			ConsumerGroup:    consumerGroup,
			Topic:            item.Topic,
			SubscriptionMode: item.Mode,
			GRPCType:         consts.WEBHOOK,
			URL:              url,
			LastUPTime:       time.Now(),
		})
	}
	for _, cli := range newClients {
		if err := consumerMgr.RegisterClient(cli); err != nil {
			return buildPBResponse(grpc.EVENTMESH_Subscribe_Register_ERR), err
		}
	}
	meshConsumer, err := consumerMgr.GetConsumer(consumerGroup)
	if err != nil {
		return buildPBResponse(grpc.EVENTMESH_Consumer_NotFound_ERR), err
	}
	requireRestart := false
	for _, cli := range newClients {
		if meshConsumer.RegisterClient(cli) {
			requireRestart = true
		}
	}
	if requireRestart {
		log.Infof("ConsumerGroup %v topic info changed, restart EventMesh Consumer", consumerGroup)
		if err := consumerMgr.RestartConsumer(consumerGroup); err != nil {
			return buildPBResponse(grpc.EVENTMESH_Consumer_NotFound_ERR), err
		}
	} else {
		log.Warnf("EventMesh consumer [%v] didn't restart.", consumerGroup)
	}
	return buildPBResponse(grpc.SUCCESS), nil
}

func (p *processor) UnSubscribe(consumerMgr ConsumerManager, msg *pb.Subscription) (*pb.Response, error) {
	hdr := msg.Header
	if err := validator.ValidateHeader(hdr); err != nil {
		log.Warnf("invalid header:%v", err)
		return buildPBResponse(grpc.EVENTMESH_PROTOCOL_HEADER_ERR), err
	}
	if err := validator.ValidateSubscription(consts.WEBHOOK, msg); err != nil {
		log.Warnf("invalid body:%v", err)
		return buildPBResponse(grpc.EVENTMESH_PROTOCOL_BODY_ERR), err
	}
	consumerGroup := msg.ConsumerGroup
	url := msg.Url
	items := msg.SubscriptionItems
	var removeClients []*GroupClient
	for _, item := range items {
		removeClients = append(removeClients, &GroupClient{
			ENV:              hdr.Env,
			IDC:              hdr.Idc,
			SYS:              hdr.Sys,
			IP:               hdr.Ip,
			PID:              hdr.Pid,
			ConsumerGroup:    consumerGroup,
			Topic:            item.Topic,
			SubscriptionMode: item.Mode,
			GRPCType:         consts.WEBHOOK,
			URL:              url,
			LastUPTime:       time.Now(),
		})
	}
	for _, cli := range removeClients {
		if err := consumerMgr.DeRegisterClient(cli); err != nil {
			return buildPBResponse(grpc.EVENTMESH_Subscribe_Register_ERR), err
		}
	}
	meshConsumer, err := consumerMgr.GetConsumer(consumerGroup)
	if err != nil {
		return buildPBResponse(grpc.EVENTMESH_Consumer_NotFound_ERR), err
	}
	requireRestart := false
	for _, cli := range removeClients {
		if meshConsumer.DeRegisterClient(cli) {
			requireRestart = true
		}
	}
	if requireRestart {
		log.Infof("ConsumerGroup %v topic info changed, restart EventMesh Consumer", consumerGroup)
		if err := consumerMgr.RestartConsumer(consumerGroup); err != nil {
			return buildPBResponse(grpc.EVENTMESH_Consumer_NotFound_ERR), err
		}
	} else {
		log.Warnf("EventMesh consumer [%v] didn't restart.", consumerGroup)
	}
	return buildPBResponse(grpc.SUCCESS), nil
}

func (p *processor) SubscribeStream(consumerMgr ConsumerManager, emiter emitter.EventEmitter, msg *pb.Subscription) error {
	hdr := msg.Header
	if err := validator.ValidateHeader(hdr); err != nil {
		log.Warnf("invalid header:%v", err)
		emiter.SendStreamResp(hdr, grpc.EVENTMESH_PROTOCOL_HEADER_ERR)
		return err
	}
	if err := validator.ValidateSubscription(consts.STREAM, msg); err != nil {
		log.Warnf("invalid body:%v", err)
		emiter.SendStreamResp(hdr, grpc.EVENTMESH_PROTOCOL_BODY_ERR)
		return err
	}
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
			GRPCType:         consts.STREAM,
			LastUPTime:       time.Now(),
			Emiter:           emiter,
		})
	}
	for _, cli := range clients {
		if err := consumerMgr.RegisterClient(cli); err != nil {
			return err
		}
	}
	meshConsumer, err := consumerMgr.GetConsumer(consumerGroup)
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
		return consumerMgr.RestartConsumer(consumerGroup)
	} else {
		log.Warnf("EventMesh consumer [%v] didn't restart.", consumerGroup)
	}

	return nil
}

func (p *processor) Heartbeat(consumerMgr ConsumerManager, msg *pb.Heartbeat) (*pb.Response, error) {
	hdr := msg.Header
	if err := validator.ValidateHeader(hdr); err != nil {
		log.Warnf("invalid header:%v", err)
		return buildPBResponse(grpc.EVENTMESH_PROTOCOL_HEADER_ERR), err
	}
	if err := validator.ValidateHeartBeat(msg); err != nil {
		log.Warnf("invalid body:%v", err)
		return buildPBResponse(grpc.EVENTMESH_PROTOCOL_BODY_ERR), err
	}
	if msg.ClientType != pb.Heartbeat_SUB {
		log.Warnf("client type err, not sub")
		return buildPBResponse(grpc.EVENTMESH_Heartbeat_Protocol_ERR), fmt.Errorf("protocol not sub")
	}
	consumerGroup := msg.ConsumerGroup
	for _, item := range msg.HeartbeatItems {
		cli := &GroupClient{
			ENV:           hdr.Env,
			IDC:           hdr.Idc,
			SYS:           hdr.Sys,
			IP:            hdr.Ip,
			PID:           hdr.Pid,
			ConsumerGroup: consumerGroup,
			Topic:         item.Topic,
			LastUPTime:    time.Now(),
		}
		consumerMgr.UpdateClientTime(cli)
	}
	return buildPBResponse(grpc.SUCCESS), nil
}

func (p *processor) ReplyMessage(ctx context.Context, producerMgr producer.ProducerManager, emiter emitter.EventEmitter, msg *pb.SimpleMessage) error {
	hdr := msg.Header
	if err := validator.ValidateHeader(hdr); err != nil {
		log.Warnf("invalid header:%v", err)
		emiter.SendStreamResp(hdr, grpc.EVENTMESH_PROTOCOL_HEADER_ERR)
		return err
	}
	if err := validator.ValidateMessage(msg); err != nil {
		log.Warnf("invalid body:%v", err)
		emiter.SendStreamResp(hdr, grpc.EVENTMESH_PROTOCOL_BODY_ERR)
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
		emiter.SendStreamResp(hdr, grpc.EVENTMESH_Plugin_NotFound_ERR)
		return ErrProtocolPluginNotFound
	}
	cevt, err := adp.ToCloudEvent(&grpc.SimpleMessageWrapper{SimpleMessage: msg})
	if err != nil {
		log.Warnf("transfer to cloud event msg err:%v", err)
		emiter.SendStreamResp(hdr, grpc.EVENTMESH_Transfer_Protocol_ERR)
		return err
	}
	emProducer, err := producerMgr.GetProducer(producerGroup)
	if err != nil {
		log.Warnf("no eventmesh producer found, err:%v, group:%v", err, producerGroup)
		emiter.SendStreamResp(hdr, grpc.EVENTMESH_Producer_Group_NotFound_ERR)
		return err
	}
	start := time.Now()
	return emProducer.Reply(
		producer.SendMessageContext{
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
				emiter.SendStreamResp(hdr, grpc.EVENTMESH_REPLY_MSG_ERR)
				log.Errorf("message|mq2eventmesh|REPLY|ReplyToServer|send2MQCost=%vms|topic=%v|bizSeqNo=%v|uniqueId=%v",
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

func buildPBResponse(code *grpc.StatusCode) *pb.Response {
	return &pb.Response{
		RespCode: code.RetCode,
		RespMsg:  code.ErrMsg,
		RespTime: fmt.Sprintf("%v", time.Now().UnixMilli()),
	}
}
