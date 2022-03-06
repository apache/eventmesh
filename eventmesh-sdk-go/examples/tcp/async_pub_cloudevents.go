package tcp

import (
	"eventmesh/common"
	"eventmesh/common/protocol"
	gtcp "eventmesh/common/protocol/tcp"
	"eventmesh/common/utils"
	"eventmesh/tcp"
	"time"

	//"eventmesh/tcp/common"
	"eventmesh/tcp/conf"
	cloudevents "github.com/cloudevents/sdk-go/v2"
	"github.com/google/uuid"
	"strconv"
)

func AsyncPubCloudEvents() {
	eventMeshIp := "127.0.0.1"
	eventMeshTcpPort := 10000
	topic := "TEST-TOPIC-TCP-ASYNC"

	// Init client
	userAgent := gtcp.UserAgent{Env: "test", Subsystem: "5023", Path: "/data/app/umg_proxy", Pid: 32893,
		Host: "127.0.0.1", Port: 8362, Version: "2.0.11", Username: "PU4283", Password: "PUPASS", Idc: "FT",
		Group: "EventmeshTestGroup", Purpose: "pub"}
	config := conf.NewEventMeshTCPClientConfig(eventMeshIp, eventMeshTcpPort, userAgent)
	client := tcp.CreateEventMeshTCPClient(*config, protocol.DefaultMessageType.CloudEvent)
	client.Init()

	// Make event to send
	event := cloudevents.NewEvent()
	event.SetID(uuid.New().String())
	event.SetSubject(topic)
	event.SetSource("example/uri")
	event.SetType(common.Constants.CLOUD_EVENTS_PROTOCOL_NAME)
	event.SetExtension(common.Constants.EVENTMESH_MESSAGE_CONST_TTL, strconv.Itoa(4*1000))
	event.SetDataContentType(cloudevents.ApplicationCloudEventsJSON)
	data := map[string]string{"hello": "EventMesh"}
	event.SetData(cloudevents.ApplicationCloudEventsJSON, utils.MarshalJsonBytes(data))

	// Publish event
	client.Publish(event, 10000)
	time.Sleep(10 * time.Second)
}
