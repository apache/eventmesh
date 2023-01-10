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

package producer

import (
	"context"
	"fmt"

	ce "github.com/cloudevents/sdk-go/v2"
	jsoniter "github.com/json-iterator/go"
	"sync"
	"time"

	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/common/protocol/grpc"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/connector"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/protocol"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/consts"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/protocol/grpc/emitter"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/protocol/grpc/validator"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
)

var (
	ErrProtocolPluginNotFound = fmt.Errorf("protocol plugin not found")

	jsonPool = sync.Pool{New: func() interface{} {
		return jsoniter.Config{
			EscapeHTML: true,
		}.Froze()
	}}
)

type Processor interface {
	AsyncMessage(ctx context.Context, producerMgr ProducerManager, msg *pb.SimpleMessage) (*pb.Response, error)
	ReplyMessage(ctx context.Context, producerMgr ProducerManager, emiter emitter.EventEmitter, msg *pb.SimpleMessage) error
	RequestReplyMessage(ctx context.Context, producerMgr ProducerManager, msg *pb.SimpleMessage) (*pb.SimpleMessage, error)
	BatchPublish(ctx context.Context, producerMgr ProducerManager, msg *pb.BatchMessage) (*pb.Response, error)
}

type processor struct {
}

func (p *processor) AsyncMessage(ctx context.Context, producerMgr ProducerManager, msg *pb.SimpleMessage) (*pb.Response, error) {
	hdr := msg.Header
	if err := validator.ValidateHeader(hdr); err != nil {
		log.Warnf("invalid header:%v", err)
		return buildPBResponse(grpc.EVENTMESH_PROTOCOL_HEADER_ERR), err
	}
	if err := validator.ValidateMessage(msg); err != nil {
		log.Warnf("invalid body:%v", err)
		return buildPBResponse(grpc.EVENTMESH_PROTOCOL_BODY_ERR), err
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
		return buildPBResponse(grpc.EVENTMESH_Plugin_NotFound_ERR), ErrProtocolPluginNotFound
	}
	cevt, err := adp.ToCloudEvent(&grpc.SimpleMessageWrapper{SimpleMessage: msg})
	if err != nil {
		return buildPBResponse(grpc.EVENTMESH_Transfer_Protocol_ERR), err
	}
	ep, err := producerMgr.GetProducer(pg)
	if err != nil {
		return buildPBResponse(grpc.EVENTMESH_PROTOCOL_BODY_ERR), err
	}
	var code *grpc.StatusCode
	if err = ep.Send(
		SendMessageContext{
			Ctx:         ctx,
			Event:       cevt,
			BizSeqNO:    seqNum,
			ProducerAPI: ep,
			CreateTime:  time.Now(),
		},
		&connector.SendCallback{
			OnSuccess: func(result *connector.SendResult) {
				code = grpc.SUCCESS
				log.Infof("message|eventMesh2mq|REQ|ASYNC|send2MQCost=%vms|topic=%v|bizSeqNo=%v|uniqueId=%v",
					time.Now().Sub(start).Milliseconds(), topic, seqNum, uid)
			},
			OnError: func(result *connector.ErrorResult) {
				code = grpc.EVENTMESH_SEND_ASYNC_MSG_ERR
				log.Errorf("message|eventMesh2mq|REQ|ASYNC|send2MQCost=%vms|topic=%v|bizSeqNo=%v|uniqueId=%v, err:%v",
					time.Now().Sub(start).Milliseconds(), topic, seqNum, uid, result.Err)
			},
		},
	); err != nil {
		log.Warnf("send message to mq err:%v", err)
	}
	return buildPBResponse(code), nil
}

func (p *processor) ReplyMessage(ctx context.Context, producerMgr ProducerManager, emiter emitter.EventEmitter, msg *pb.SimpleMessage) error {
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
				emiter.SendStreamResp(hdr, grpc.EVENTMESH_REPLY_MSG_ERR)
				log.Errorf("message|mq2eventmesh|REPLY|ReplyToServer|send2MQCost=%vms|topic=%v|bizSeqNo=%v|uniqueId=%v",
					time.Now().Sub(start).Milliseconds(), replyTopic, seqNum, uniqID, result.Err)
			},
		},
	)
}

func (p *processor) RequestReplyMessage(ctx context.Context, producerMgr ProducerManager, msg *pb.SimpleMessage) (*pb.SimpleMessage, error) {
	var (
		err  error
		resp *pb.SimpleMessage
		hdr  = msg.Header
	)
	if err = validator.ValidateHeader(hdr); err != nil {
		log.Warnf("invalid header:%v", err)
		return buildPBSimpleMessage(hdr, grpc.EVENTMESH_PROTOCOL_HEADER_ERR), err
	}
	if err = validator.ValidateMessage(msg); err != nil {
		log.Warnf("invalid body:%v", err)
		return buildPBSimpleMessage(hdr, grpc.EVENTMESH_PROTOCOL_BODY_ERR), err
	}
	protocolType := hdr.ProtocolType
	adp := plugin.Get(plugin.Protocol, protocolType).(protocol.Adapter)
	if adp == nil {
		log.Warnf("protocol plugin not found:%v", protocolType)
		return buildPBSimpleMessage(hdr, grpc.EVENTMESH_Plugin_NotFound_ERR), ErrProtocolPluginNotFound
	}
	cevt, err := adp.ToCloudEvent(&grpc.SimpleMessageWrapper{SimpleMessage: msg})
	if err != nil {
		log.Warnf("transfer to cloud event msg err:%v", err)
		return buildPBSimpleMessage(hdr, grpc.EVENTMESH_Transfer_Protocol_ERR), err
	}
	seqNum := msg.SeqNum
	unidID := msg.UniqueId
	topic := msg.Topic
	producerGroup := msg.ProducerGroup
	ttl, _ := StringToDuration(msg.Ttl)
	start := time.Now()
	ep, err := producerMgr.GetProducer(producerGroup)
	if err != nil {
		return buildPBSimpleMessage(hdr, grpc.EVENTMESH_PROTOCOL_BODY_ERR), err
	}

	if err = ep.Request(
		SendMessageContext{
			Ctx:         ctx,
			Event:       cevt,
			BizSeqNO:    seqNum,
			ProducerAPI: ep,
			CreateTime:  time.Now(),
		},
		&connector.RequestReplyCallback{
			OnSuccess: func(event *ce.Event) {
				log.Infof("message|eventmesh2client|REPLY|RequestReply|send2MQCost=%vms|topic=%v|bizSeqNo=%v|uniqueId=%v",
					time.Now().Sub(start).Milliseconds(), topic, seqNum, unidID)

				m1, err1 := adp.FromCloudEvent(event)
				if err1 != nil {
					log.Warnf("failed to transfer msg from event, err:%v", err)
					err = grpc.EVENTMESH_Transfer_Protocol_ERR.ToError()
					return
				}
				resp = m1.(grpc.SimpleMessageWrapper).SimpleMessage
			},
			OnError: func(result *connector.ErrorResult) {
				log.Errorf("message|mq2eventmesh|REPLY|RequestReply|send2MQCost=%vms|topic=%v|bizSeqNo=%v|uniqueId=%v|err=%v",
					time.Now().Sub(start).Milliseconds(), topic, seqNum, unidID, err)
				err = grpc.EVENTMESH_REQUEST_REPLY_MSG_ERR.ToError()
			},
		},
		ttl); err != nil {
		log.Warnf("failed to request message, uniqID:%v, err:%v", unidID, err)
		return nil, err
	}
	return resp, err
}

func (p *processor) BatchPublish(ctx context.Context, producerMgr ProducerManager, msg *pb.BatchMessage) (*pb.Response, error) {
	var (
		err error
		hdr = msg.Header
	)
	if err = validator.ValidateHeader(hdr); err != nil {
		log.Warnf("invalid header:%v", err)
		return buildPBResponse(grpc.EVENTMESH_PROTOCOL_HEADER_ERR), err
	}
	if err = validator.ValidateBatchMessage(msg); err != nil {
		log.Warnf("invalid body:%v", err)
		return buildPBResponse(grpc.EVENTMESH_PROTOCOL_BODY_ERR), err
	}
	protocolType := hdr.ProtocolType
	adp := plugin.Get(plugin.Protocol, protocolType).(protocol.Adapter)
	if adp == nil {
		log.Warnf("protocol plugin not found:%v", protocolType)
		return buildPBResponse(grpc.EVENTMESH_Plugin_NotFound_ERR), ErrProtocolPluginNotFound
	}
	cevts, err := adp.ToCloudEvents(&grpc.BatchMessageWrapper{BatchMessage: msg})
	if err != nil {
		log.Warnf("transfer to cloud event msg err:%v", err)
		return buildPBResponse(grpc.EVENTMESH_Transfer_Protocol_ERR), err
	}
	topic := msg.Topic
	producerGroup := msg.ProducerGroup
	ep, err := producerMgr.GetProducer(producerGroup)
	if err != nil {
		return buildPBResponse(grpc.EVENTMESH_PROTOCOL_BODY_ERR), err
	}
	// TODO use errorgroup instead
	for _, evt := range cevts {
		seqNum := evt.ID()
		uid := defaultIfEmpty(evt.Extensions()[grpc.UNIQUE_ID], "")
		start := time.Now()
		ep.Send(
			SendMessageContext{
				Ctx:         ctx,
				Event:       evt,
				BizSeqNO:    seqNum,
				ProducerAPI: ep,
				CreateTime:  time.Now(),
			},
			&connector.SendCallback{
				OnSuccess: func(result *connector.SendResult) {
					log.Infof("message|eventMesh2mq|REQ|BatchSend|send2MQCost=%vms|topic=%v|bizSeqNo=%v|uniqueId=%v",
						time.Now().Sub(start).Milliseconds(), topic, seqNum, uid)
				},
				OnError: func(result *connector.ErrorResult) {
					log.Errorf("message|eventMesh2mq|REQ|BatchSend|send2MQCost=%vms|topic=%v|bizSeqNo=%v|uniqueId=%v, err:%v",
						time.Now().Sub(start).Milliseconds(), topic, seqNum, uid, result.Err)
				},
			},
		)
	}
	return buildPBResponse(grpc.SUCCESS), nil
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

func buildPBSimpleMessage(hdr *pb.RequestHeader, code *grpc.StatusCode) *pb.SimpleMessage {
	mm := map[string]string{
		consts.RESP_CODE: code.RetCode,
		consts.RESP_MSG:  code.ErrMsg,
	}
	content, _ := jsonPool.Get().(jsoniter.API).MarshalToString(mm)
	return &pb.SimpleMessage{
		Header:  hdr,
		Content: content,
	}
}

func StringToDuration(in string) (time.Duration, error) {
	return time.ParseDuration(in)
}
