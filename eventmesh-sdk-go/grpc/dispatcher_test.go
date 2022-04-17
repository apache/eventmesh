package grpc

import (
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/proto"
	"github.com/stretchr/testify/assert"
	"sync"
	"testing"
)

func Test_messageDispatcher_addHandler(t *testing.T) {
	type fields struct {
		topicMap *sync.Map
		poolsize int
	}
	type args struct {
		topic string
		hdl   OnMessage
	}
	tests := []struct {
		name    string
		fields  fields
		args    args
		wantErr assert.ErrorAssertionFunc
	}{
		{
			name: "test add handler",
			fields: fields{
				topicMap: new(sync.Map),
				poolsize: 5,
			},
			args: args{
				topic: "handler-1",
				hdl: func(message *proto.SimpleMessage) {
					t.Logf("handle message")
				},
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			m := &messageDispatcher{
				topicMap: tt.fields.topicMap,
				poolsize: tt.fields.poolsize,
			}
			tt.wantErr(t, m.addHandler(tt.args.topic, tt.args.hdl), fmt.Sprintf("addHandler(%v, %v)", tt.args.topic, tt.args.hdl))
		})
	}
}
