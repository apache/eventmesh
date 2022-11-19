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
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
	"github.com/gin-gonic/gin"
	"github.com/stretchr/testify/assert"
	"google.golang.org/grpc"
	"io"
	"net/http"
	"testing"
)

var (
	_testStreamTopic  = "test-grpc-stream-topic"
	_testWebhookTopic = "test-grpc-webhook-topic"
)

// testClient test client to do the grpc service test
type testClient struct {
	consumerClient  pb.ConsumerServiceClient
	producerClient  pb.PublisherServiceClient
	heartbeatClient pb.HeartbeatServiceClient
}

func newTestClient(t *testing.T) *testClient {
	clientCoon, err := grpc.Dial("127.0.0.1:10010", grpc.WithInsecure())
	assert.NoError(t, err)
	consumer := pb.NewConsumerServiceClient(clientCoon)
	producer := pb.NewPublisherServiceClient(clientCoon)
	heartheat := pb.NewHeartbeatServiceClient(clientCoon)
	return &testClient{
		consumerClient:  consumer,
		producerClient:  producer,
		heartbeatClient: heartheat,
	}
}

func (c *testClient) startWebhookServer(t *testing.T) error {
	router := gin.Default()

	// TestResponse indicate the http response
	type TestResponse struct {
		RetCode string `json:"retCode"`
		ErrMsg  string `json:"errMsg"`
	}

	router.Any("/*anypath", func(c *gin.Context) {
		c.JSON(http.StatusOK, &TestResponse{
			RetCode: "0",
			ErrMsg:  "OK",
		})
	})
	go func() {
		if err := router.Run(fmt.Sprintf(":%d", 18080)); err != nil {
			panic(err)
		}
	}()
	return nil
}

func (c *testClient) startStreamServer(t *testing.T) error {
	clientCoon, err := grpc.Dial("127.0.0.1:10010", grpc.WithInsecure())
	assert.NoError(t, err)
	grpcClient := pb.NewConsumerServiceClient(clientCoon)
	stream, err := grpcClient.SubscribeStream(context.TODO())
	assert.NoError(t, err)
	go func() {
		for {
			resp, err := stream.Recv()
			if err == io.EOF {
				t.Log("server closed")
				break
			}
			if err != nil {
				t.Logf("server err:%v", err)
			}
			t.Logf("receive:%v", resp.Content)
		}
	}()
	return nil
}

func Subscribe(ctx context.Context, sub *pb.Subscription) (*pb.Response, error) {
	return nil, nil
}

func SubscribeStream(sss pb.ConsumerService_SubscribeStreamServer) error {
	return nil
}

func Unsubscribe(ctx context.Context, unsub *pb.Subscription) (*pb.Response, error) {
	return nil, nil
}

// producer service
func Publish(ctx context.Context, msg *pb.SimpleMessage) (*pb.Response, error) {
	return nil, nil
}

func RequestReply(ctx context.Context, msg *pb.SimpleMessage) (*pb.SimpleMessage, error) {
	return nil, nil
}

func BatchPublish(ctx context.Context, msg *pb.BatchMessage) (*pb.Response, error) {
	return nil, nil
}

// heartbeat service
func Heartbeat(ctx context.Context, msg *pb.Heartbeat) (*pb.Response, error) {
	return nil, nil
}
