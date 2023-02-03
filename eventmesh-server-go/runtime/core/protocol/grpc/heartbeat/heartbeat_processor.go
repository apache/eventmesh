/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package heartbeat

import (
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/common/protocol/grpc"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/protocol/grpc/consumer"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/protocol/grpc/validator"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
	"time"
)

type Processor interface {
	Heartbeat(consumerMgr consumer.ConsumerManager, msg *pb.Heartbeat) (*pb.Response, error)
}

type processor struct {
}

func (p *processor) Heartbeat(consumerMgr consumer.ConsumerManager, msg *pb.Heartbeat) (*pb.Response, error) {
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
		cli := &consumer.GroupClient{
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

func buildPBResponse(code *grpc.StatusCode) *pb.Response {
	return &pb.Response{
		RespCode: code.RetCode,
		RespMsg:  code.ErrMsg,
		RespTime: fmt.Sprintf("%v", time.Now().UnixMilli()),
	}
}
