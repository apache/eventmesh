package grpc

import (
	"context"
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/conf"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/proto"
	"github.com/stretchr/testify/assert"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	"testing"
)

func Test_newConsumer(t *testing.T) {
	// run fake server
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go runFakeServer(ctx)
	type args struct {
		ctx      context.Context
		cfg      *conf.GRPCConfig
		connsMap map[string]*grpc.ClientConn
	}
	addr := fmt.Sprintf("%v:%v", "127.0.0.1", 8086)
	conn, err := grpc.Dial(addr, grpc.WithTransportCredentials(insecure.NewCredentials()))
	assert.NotNil(t, err)
	tests := []struct {
		name    string
		args    args
		want    *eventMeshConsumer
		wantErr assert.ErrorAssertionFunc
	}{
		{
			name: "create new",
			args: args{
				cfg: &conf.GRPCConfig{
					Hosts: []string{"127.0.0.1"},
					Port:  8086,
				},
				connsMap: map[string]*grpc.ClientConn{
					"127.0.0.1": conn,
				},
			},
			wantErr: func(t assert.TestingT, err error, i ...interface{}) bool {
				return true
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := newConsumer(tt.args.ctx, tt.args.cfg, tt.args.connsMap)
			if !tt.wantErr(t, err, fmt.Sprintf("newConsumer(%v, %v, %v)", tt.args.ctx, tt.args.cfg, tt.args.connsMap)) {
				return
			}
			assert.Equalf(t, tt.want, got, "newConsumer(%v, %v, %v)", tt.args.ctx, tt.args.cfg, tt.args.connsMap)
		})
	}
}

func Test_eventMeshConsumer_Subscribe(t *testing.T) {
	type fields struct {
		consumerMap map[string]proto.ConsumerServiceClient
		topics      map[string]*proto.Subscription_SubscriptionItem
		cfg         *conf.GRPCConfig
		dispatcher  *messageDispatcher
		heartbeat   *eventMeshHeartbeat
		closeCtx    context.Context
	}
	type args struct {
		item    conf.SubscribeItem
		handler OnMessage
	}
	tests := []struct {
		name    string
		fields  fields
		args    args
		wantErr assert.ErrorAssertionFunc
	}{
		// TODO: Add test cases.
	}
	// run fake server
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go runFakeServer(ctx)
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			d := &eventMeshConsumer{
				consumerMap: tt.fields.consumerMap,
				topics:      tt.fields.topics,
				cfg:         tt.fields.cfg,
				dispatcher:  tt.fields.dispatcher,
				heartbeat:   tt.fields.heartbeat,
				closeCtx:    tt.fields.closeCtx,
			}
			tt.wantErr(t, d.Subscribe(tt.args.item, tt.args.handler), fmt.Sprintf("Subscribe(%v, %v)", tt.args.item, tt.args.handler))
		})
	}
}
