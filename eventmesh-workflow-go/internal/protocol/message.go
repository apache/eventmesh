package protocol

import (
	"context"
	pgrpc "github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/constants"
)

var messageBuilder map[string]Message

// Message workflow message definition
type Message interface {
	Publish(ctx context.Context, string, content string, properties map[string]string) error
}

func closeEventMeshClient(client pgrpc.Interface) {
	if client != nil {
		if err := client.Close(); err != nil {
			log.Get(constants.LogSchedule).Errorf("close eventmesh client error:%v", err)
		}
	}
}

func Builder(protocol string) Message {
	return messageBuilder[protocol]
}
