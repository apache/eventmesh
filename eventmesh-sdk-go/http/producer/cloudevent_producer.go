package producer

import (
	gcommon "eventmesh/common"
	"eventmesh/common/protocol/http/common"
	"eventmesh/common/protocol/http/message"
	gutils "eventmesh/common/utils"
	"eventmesh/http"
	"eventmesh/http/conf"
	"eventmesh/http/model"
	"eventmesh/http/utils"
	cloudevents "github.com/cloudevents/sdk-go/v2"
	"log"
	nethttp "net/http"
	"strconv"
)

type CloudEventProducer struct {
	*http.AbstractHttpClient
}

func NewCloudEventProducer(eventMeshHttpClientConfig conf.EventMeshHttpClientConfig) *CloudEventProducer {
	c := &CloudEventProducer{AbstractHttpClient: http.NewAbstractHttpClient(eventMeshHttpClientConfig)}
	return c
}

func (c *CloudEventProducer) Publish(event cloudevents.Event) {
	enhancedEvent := c.enhanceCloudEvent(event)
	requestParam := c.buildCommonPostParam(enhancedEvent)
	requestParam.AddHeader(common.ProtocolKey.REQUEST_CODE, strconv.Itoa(common.DefaultRequestCode.MSG_SEND_ASYNC.RequestCode))

	target := c.SelectEventMesh()
	resp := utils.HttpPost(c.HttpClient, target, requestParam)
	var ret http.EventMeshRetObj
	gutils.UnMarshalJsonString(resp, &ret)
	if ret.RetCode != common.DefaultEventMeshRetCode.SUCCESS.RetCode {
		log.Fatalf("Request failed, error code: %d", ret.RetCode)
	}
}

func (c *CloudEventProducer) buildCommonPostParam(event cloudevents.Event) *model.RequestParam {

	eventBytes, err := event.MarshalJSON()
	if err != nil {
		log.Fatal("Failed to marshal cloudevent")
	}
	content := string(eventBytes)

	requestParam := model.NewRequestParam(nethttp.MethodPost)
	requestParam.AddHeader(common.ProtocolKey.ClientInstanceKey.ENV, c.EventMeshHttpClientConfig.Env())
	requestParam.AddHeader(common.ProtocolKey.ClientInstanceKey.IDC, c.EventMeshHttpClientConfig.Idc())
	requestParam.AddHeader(common.ProtocolKey.ClientInstanceKey.IP, c.EventMeshHttpClientConfig.Ip())
	requestParam.AddHeader(common.ProtocolKey.ClientInstanceKey.PID, c.EventMeshHttpClientConfig.Pid())
	requestParam.AddHeader(common.ProtocolKey.ClientInstanceKey.SYS, c.EventMeshHttpClientConfig.Sys())
	requestParam.AddHeader(common.ProtocolKey.ClientInstanceKey.USERNAME, c.EventMeshHttpClientConfig.UserName())
	requestParam.AddHeader(common.ProtocolKey.ClientInstanceKey.PASSWORD, c.EventMeshHttpClientConfig.Password())
	requestParam.AddHeader(common.ProtocolKey.LANGUAGE, gcommon.Constants.LANGUAGE_GO)
	// FIXME Improve constants
	requestParam.AddHeader(common.ProtocolKey.PROTOCOL_TYPE, "cloudevents")
	requestParam.AddHeader(common.ProtocolKey.PROTOCOL_DESC, "http")
	requestParam.AddHeader(common.ProtocolKey.PROTOCOL_VERSION, event.SpecVersion())

	// todo: move producerGroup tp header
	requestParam.AddBody(message.SendMessageRequestBodyKey.PRODUCERGROUP, c.EventMeshHttpClientConfig.ProducerGroup())
	requestParam.AddBody(message.SendMessageRequestBodyKey.CONTENT, content)

	return requestParam
}

func (c *CloudEventProducer) enhanceCloudEvent(event cloudevents.Event) cloudevents.Event {
	event.SetExtension(common.ProtocolKey.ClientInstanceKey.ENV, c.EventMeshHttpClientConfig.Env())
	event.SetExtension(common.ProtocolKey.ClientInstanceKey.IDC, c.EventMeshHttpClientConfig.Idc())
	event.SetExtension(common.ProtocolKey.ClientInstanceKey.IP, c.EventMeshHttpClientConfig.Ip())
	event.SetExtension(common.ProtocolKey.ClientInstanceKey.PID, c.EventMeshHttpClientConfig.Pid())
	event.SetExtension(common.ProtocolKey.ClientInstanceKey.SYS, c.EventMeshHttpClientConfig.Sys())
	// FIXME Random string
	event.SetExtension(common.ProtocolKey.ClientInstanceKey.BIZSEQNO, "333333")
	event.SetExtension(common.ProtocolKey.ClientInstanceKey.UNIQUEID, "444444")
	event.SetExtension(common.ProtocolKey.LANGUAGE, gcommon.Constants.LANGUAGE_GO)
	// FIXME Java is name of spec version name
	//event.SetExtension(common.ProtocolKey.PROTOCOL_DESC, event.SpecVersion())
	event.SetExtension(common.ProtocolKey.PROTOCOL_VERSION, event.SpecVersion())

	return event
}
