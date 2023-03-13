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
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
	"github.com/panjf2000/ants/v2"
	"time"
)

var defaultAsyncTimeout = time.Second * 5

type ProducerService struct {
	pb.UnimplementedPublisherServiceServer
	producerMgr ProducerManager
	process     Processor
	sendPool    *ants.Pool
}

func NewProducerServiceServer(producerMgr ProducerManager) (*ProducerService, error) {
	ps := config.GlobalConfig().Server.GRPCOption.SendPoolSize
	pl, err := ants.NewPool(ps)
	if err != nil {
		return nil, err
	}
	return &ProducerService{
		producerMgr: producerMgr,
		sendPool:    pl,
		process:     &processor{},
	}, nil
}

func (p *ProducerService) Publish(ctx context.Context, msg *pb.SimpleMessage) (*pb.Response, error) {
	ctx = context.WithValue(ctx, "UNIQID", msg.UniqueId)
	log.Infof("cmd:%v, protocol:grpc, from:%v", "AsyncPublish", msg.Header.Ip)
	tmCtx, cancel := context.WithTimeout(ctx, defaultAsyncTimeout)
	defer cancel()
	var (
		resp    *pb.Response
		errChan = make(chan error)
		err     error
	)
	p.sendPool.Submit(func() {
		resp, err = p.process.AsyncMessage(ctx, p.producerMgr, msg)
		errChan <- err
	})
	select {
	case <-tmCtx.Done():
		log.Warnf("timeout in subscribe")
	case <-errChan:
		break
	}
	if err != nil {
		log.Warnf("failed to handle publish, err:%v", err)
	}
	return resp, err
}

func (p *ProducerService) RequestReply(ctx context.Context, msg *pb.SimpleMessage) (*pb.SimpleMessage, error) {
	log.Infof("cmd=%v|%v|client2eventMesh|from=%v", "RequestReply", "grpc", msg.Header.Ip)
	tmCtx, cancel := context.WithTimeout(ctx, defaultAsyncTimeout)
	defer cancel()
	var (
		resp    *pb.SimpleMessage
		errChan = make(chan error)
		err     error
	)
	p.sendPool.Submit(func() {
		resp, err = p.process.RequestReplyMessage(ctx, p.producerMgr, msg)
		errChan <- err
	})
	select {
	case <-tmCtx.Done():
		log.Warnf("timeout in subscribe")
	case <-errChan:
		break
	}
	if err != nil {
		log.Warnf("failed to handle request reply msg, err:%v", err)
	}
	return resp, err
}

func (p *ProducerService) BatchPublish(ctx context.Context, msg *pb.BatchMessage) (*pb.Response, error) {
	log.Infof("cmd=%v|%v|client2eventMesh|from=%v", "BatchPublish", "grpc", msg.Header.Ip)

	tmCtx, cancel := context.WithTimeout(ctx, defaultAsyncTimeout)
	defer cancel()
	var (
		resp    *pb.Response
		errChan = make(chan error)
		err     error
	)
	p.sendPool.Submit(func() {
		resp, err = p.process.BatchPublish(ctx, p.producerMgr, msg)
		errChan <- err
	})
	select {
	case <-tmCtx.Done():
		log.Warnf("timeout in subscribe")
	case <-errChan:
		break
	}
	if err != nil {
		log.Warnf("failed to handle batch publish reply msg, err:%v", err)
	}
	return resp, err
}
