package protocol

import (
	"context"
	pgrpc "github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/conf"
	eventmesh "github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/proto"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/constants"
	"github.com/gogf/gf/util/gconv"
	"github.com/google/uuid"
)

func init() {
	messageBuilder["meshmessage"] = &MeshMessage{}
}

// MeshMessage eventmesh message definition
type MeshMessage struct {
}

func (m *MeshMessage) Publish(ctx context.Context, topic string, content string, properties map[string]string) error {
	eventmeshCfg := config.Get()
	cfg := &conf.GRPCConfig{
		Host:         eventmeshCfg.EventMesh.Host,
		Port:         eventmeshCfg.EventMesh.GRPC.Port,
		ENV:          eventmeshCfg.EventMesh.Env,
		IDC:          eventmeshCfg.EventMesh.IDC,
		SYS:          eventmeshCfg.EventMesh.Sys,
		Username:     eventmeshCfg.EventMesh.UserName,
		Password:     eventmeshCfg.EventMesh.Password,
		ProtocolType: pgrpc.EventmeshMessage,
		ProducerConfig: conf.ProducerConfig{
			ProducerGroup: eventmeshCfg.EventMesh.ProducerGroup,
		},
	}
	client, err := pgrpc.New(cfg)
	if err != nil {
		return err
	}
	defer closeEventMeshClient(client)
	message := &eventmesh.SimpleMessage{
		Header:        pgrpc.CreateHeader(cfg),
		ProducerGroup: eventmeshCfg.EventMesh.ProducerGroup,
		Topic:         topic,
		Content:       content,
		Ttl:           gconv.String(eventmeshCfg.EventMesh.TTL),
		UniqueId:      uuid.New().String(),
		SeqNum:        uuid.New().String(),
		Properties:    properties,
	}
	resp, err := client.Publish(ctx, message)
	if err != nil {
		return err
	}
	log.Get(constants.LogSchedule).Debugf("publish event result: %v", resp.String())
	return nil
}
