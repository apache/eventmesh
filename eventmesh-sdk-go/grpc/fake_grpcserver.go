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
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/seq"
	"io"
	"io/ioutil"
	"net"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/id"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/proto"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/log"

	"google.golang.org/grpc"
)

// fakeServer used to do the test
type fakeServer struct {
	proto.UnsafeConsumerServiceServer
	proto.UnimplementedPublisherServiceServer
	proto.UnimplementedHeartbeatServiceServer
	idg      id.Interface
	seq      seq.Interface
	closeCtx context.Context
}

// newFakeServer create new fake grpc server for eventmesh
func runFakeServer(ctx context.Context) error {
	lis, err := net.Listen("tcp", ":8086")
	if err != nil {
		return err
	}
	f := &fakeServer{
		idg:      id.NewUUID(),
		seq:      seq.NewAtomicSeq(),
		closeCtx: ctx,
	}
	srv := grpc.NewServer()
	proto.RegisterConsumerServiceServer(srv, f)
	proto.RegisterHeartbeatServiceServer(srv, f)
	proto.RegisterPublisherServiceServer(srv, f)
	go func() {
		select {
		case <-ctx.Done():
			srv.GracefulStop()
		}
	}()
	log.Infof("serve fake server on:%v", srv.GetServiceInfo())
	if err := srv.Serve(lis); err != nil {
		log.Warnf("create fake server err:%v", err)
		return err
	}
	log.Infof("stop fake server")
	return nil
}

// Subscribe The subscribed event will be delivered by invoking the webhook url in the Subscription
func (f *fakeServer) Subscribe(ctx context.Context, msg *proto.Subscription) (*proto.Response, error) {
	log.Infof("fake-server, receive subcribe request:%v", msg.String())
	go func() {
		// send a fake msg by webhook
		resp, err := http.Post(msg.Url, "Content-Type:application/json", strings.NewReader("msg from webhook"))
		if err != nil {
			log.Errorf("err in create http webhook url:%s, err:%v", msg.Url, err)
			return
		}
		buf, _ := ioutil.ReadAll(resp.Body)
		log.Infof("send webhook msg success", string(buf))
	}()
	return &proto.Response{
		RespCode: "0",
		RespMsg:  "OK",
		RespTime: time.Now().Format("2006-01-02 15:04:05"),
	}, nil
}

// SubscribeStream  The subscribed event will be delivered through stream of Message
func (f *fakeServer) SubscribeStream(srv proto.ConsumerService_SubscribeStreamServer) error {
	wg := new(sync.WaitGroup)
	wg.Add(2)
	go func() {
		defer wg.Done()
		for {
			sub, err := srv.Recv()
			if err == io.EOF {
				log.Infof("return sub as rece io.EOF")
				break
			}
			if err != nil {
				log.Warnf("return sub as rece err:%v", err)
				break
			}
			log.Infof("rece sub:%s", sub.String())
		}
	}()
	go func() {
		defer wg.Done()
		var index int = 0
		for {
			msg := &proto.SimpleMessage{
				Header:        &proto.RequestHeader{},
				ProducerGroup: "substream-fake-group",
				Topic:         "fake-topic",
				Content:       fmt.Sprintf("%v msg from fake server", index),
				Ttl:           fmt.Sprintf("%v", time.Hour.Seconds()*24),
				UniqueId:      f.idg.Next(),
				SeqNum:        f.seq.Next(),
				Tag:           "substream-fake-tag",
				Properties: map[string]string{
					"from":    "fake",
					"service": "substream",
				},
			}
			err := srv.Send(msg)
			if err == io.EOF {
				log.Infof("return send as rece io.EOF")
				break
			}
			if err != nil {
				log.Warnf("return send as rece err:%v", err)
				break
			}
			log.Infof("send msg:%s", msg.String())
			index++
			time.Sleep(time.Second * 5)
		}
	}()
	wg.Wait()
	log.Infof("close SubscribeStream")
	return nil
}

func (f *fakeServer) Unsubscribe(ctx context.Context, msg *proto.Subscription) (*proto.Response, error) {
	log.Infof("fake-server, receive unsubcribe request:%v", msg.String())
	return &proto.Response{
		RespCode: "OK",
		RespMsg:  "OK",
		RespTime: time.Now().Format("2006-01-02 15:04:05"),
	}, nil
}

func (f *fakeServer) Heartbeat(ctx context.Context, msg *proto.Heartbeat) (*proto.Response, error) {
	log.Infof("fake-server, receive heartbeat request:%v", msg.String())
	return &proto.Response{
		RespCode: "OK",
		RespMsg:  "OK",
		RespTime: time.Now().Format("2006-01-02 15:04:05"),
	}, nil
}

// Publish Async event publish
func (f *fakeServer) Publish(ctx context.Context, msg *proto.SimpleMessage) (*proto.Response, error) {
	log.Infof("fake-server, receive publish request:%v", msg.String())
	return &proto.Response{
		RespCode: "OK",
		RespMsg:  "OK",
		RespTime: time.Now().Format("2006-01-02 15:04:05"),
	}, nil
}

// RequestReply Sync event publish
func (f *fakeServer) RequestReply(ctx context.Context, rece *proto.SimpleMessage) (*proto.SimpleMessage, error) {
	log.Infof("receive request reply topic:%s, content:%s", rece.Topic, rece.Content)
	return &proto.SimpleMessage{
		Header:        rece.Header,
		ProducerGroup: "fake-mock-group",
		Topic:         rece.Topic,
		Content:       "fake response for request reply" + rece.Content,
		Ttl:           fmt.Sprintf("%v", time.Hour.Seconds()*24),
		UniqueId:      f.idg.Next(),
		SeqNum:        f.seq.Next(),
		Tag:           "fake-tag",
		Properties: map[string]string{
			"from":    "fake",
			"service": "RequestReply",
		},
	}, nil
}

// BatchPublish Async batch event publish
func (f *fakeServer) BatchPublish(ctx context.Context, rece *proto.BatchMessage) (*proto.Response, error) {
	log.Infof("receive batch publish topic:%s, content len:%s", rece.Topic, len(rece.MessageItem))
	return &proto.Response{
		RespCode: "OK",
		RespMsg:  "Response for batchpublish",
		RespTime: time.Now().Format("2006-01-02 15:04:05"),
	}, nil
}
