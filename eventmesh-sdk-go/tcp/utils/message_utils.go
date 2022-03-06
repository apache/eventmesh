package utils

import (
	gcommon "eventmesh/common"
	"eventmesh/common/protocol/tcp"
	"eventmesh/tcp/common"
	cloudevents "github.com/cloudevents/sdk-go/v2"
	"log"
)

func BuildPackage(message interface{}, command tcp.Command) tcp.Package {
	// FIXME Support random sequence
	header := tcp.NewHeader(command, 0, "", "22222")
	pkg := tcp.NewPackage(header)

	if _, ok := message.(cloudevents.Event); ok {
		event := message.(cloudevents.Event)
		eventBytes, err := event.MarshalJSON()
		if err != nil {
			log.Fatal("Failed to marshal cloud event")
		}

		pkg.Header.PutProperty(gcommon.Constants.PROTOCOL_TYPE, common.EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME)
		pkg.Header.PutProperty(gcommon.Constants.PROTOCOL_VERSION, event.SpecVersion())
		pkg.Header.PutProperty(gcommon.Constants.PROTOCOL_DESC, "tcp")
		pkg.Body = eventBytes
	}

	return pkg
}

func BuildHelloPackage(agent tcp.UserAgent) tcp.Package {
	// FIXME Support random sequence
	header := tcp.NewHeader(tcp.DefaultCommand.HELLO_REQUEST, 0, "", "22222")
	msg := tcp.NewPackage(header)
	msg.Body = agent
	return msg
}

func BuildHeartBeatPackage() tcp.Package {
	// FIXME Support random sequence
	header := tcp.NewHeader(tcp.DefaultCommand.HEARTBEAT_REQUEST, 0, "", "22222")
	msg := tcp.NewPackage(header)
	return msg
}
