package http

import (
	"eventmesh/common"
	"eventmesh/common/utils"
	"eventmesh/http/conf"
	"eventmesh/http/producer"
	cloudevents "github.com/cloudevents/sdk-go/v2"
	"github.com/google/uuid"
	"log"
	"os"
	"strconv"
)

func AsyncPubCloudEvents() {
	eventMeshIPPort := "127.0.0.1" + ":" + "10105"
	producerGroup := "EventMeshTest-producerGroup"
	topic := "TEST-TOPIC-HTTP-ASYNC"
	env := "P"
	idc := "FT"
	subSys := "1234"
	// FIXME Get ip dynamically
	localIp := "127.0.0.1"

	// (Deep) Copy of default config
	eventMeshClientConfig := conf.DefaultEventMeshHttpClientConfig
	eventMeshClientConfig.SetLiteEventMeshAddr(eventMeshIPPort)
	eventMeshClientConfig.SetProducerGroup(producerGroup)
	eventMeshClientConfig.SetEnv(env)
	eventMeshClientConfig.SetIdc(idc)
	eventMeshClientConfig.SetSys(subSys)
	eventMeshClientConfig.SetIp(localIp)
	eventMeshClientConfig.SetPid(strconv.Itoa(os.Getpid()))

	// Make event to send
	event := cloudevents.NewEvent()
	event.SetID(uuid.New().String())
	event.SetSubject(topic)
	event.SetSource("example/uri")
	event.SetType(common.Constants.CLOUD_EVENTS_PROTOCOL_NAME)
	event.SetExtension(common.Constants.EVENTMESH_MESSAGE_CONST_TTL, strconv.Itoa(4*1000))
	event.SetDataContentType(cloudevents.ApplicationCloudEventsJSON)
	data := map[string]string{"hello": "EventMesh"}
	err := event.SetData(cloudevents.ApplicationCloudEventsJSON, utils.MarshalJsonBytes(data))
	if err != nil {
		log.Fatalf("Failed to set cloud event data, error: %v", err)
	}

	// Publish event
	httpProducer := producer.NewEventMeshHttpProducer(eventMeshClientConfig)
	httpProducer.Publish(event)
}
