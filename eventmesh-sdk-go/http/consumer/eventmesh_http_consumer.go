package consumer

import (
	gcommon "eventmesh/common"
	"eventmesh/common/protocol"
	"eventmesh/common/protocol/http/body/client"
	"eventmesh/common/protocol/http/common"
	gutils "eventmesh/common/utils"
	"eventmesh/http"
	"eventmesh/http/conf"
	"eventmesh/http/model"
	"eventmesh/http/utils"
	"log"
	nethttp "net/http"
	"strconv"
	"time"
)

type EventMeshHttpConsumer struct {
	*http.AbstractHttpClient
	subscriptions []protocol.SubscriptionItem
}

func NewEventMeshHttpConsumer(eventMeshHttpClientConfig conf.EventMeshHttpClientConfig) *EventMeshHttpConsumer {
	c := &EventMeshHttpConsumer{AbstractHttpClient: http.NewAbstractHttpClient(eventMeshHttpClientConfig)}
	c.subscriptions = make([]protocol.SubscriptionItem, 1000)
	return c
}

func (e *EventMeshHttpConsumer) HeartBeat(topicList []protocol.SubscriptionItem, subscribeUrl string) {

	// FIXME check topicList, subscribeUrl is not blank

	for range time.Tick(time.Duration(gcommon.Constants.HEARTBEAT) * time.Millisecond) {

		var heartbeatEntities []client.HeartbeatEntity
		for _, item := range topicList {
			entity := client.HeartbeatEntity{Topic: item.Topic, Url: subscribeUrl}
			heartbeatEntities = append(heartbeatEntities, entity)
		}

		requestParam := e.buildCommonRequestParam()
		requestParam.AddHeader(common.ProtocolKey.REQUEST_CODE, strconv.Itoa(common.DefaultRequestCode.HEARTBEAT.RequestCode))
		// FIXME Java is name of SUB name
		//requestParam.AddBody(client.HeartbeatRequestBodyKey.CLIENTTYPE, common.DefaultClientType.SUB.name())
		requestParam.AddBody(client.HeartbeatRequestBodyKey.CLIENTTYPE, "SUB")
		requestParam.AddBody(client.HeartbeatRequestBodyKey.HEARTBEATENTITIES, gutils.MarshalJsonString(heartbeatEntities))

		target := e.SelectEventMesh()
		resp := utils.HttpPost(e.HttpClient, target, requestParam)
		var ret http.EventMeshRetObj
		gutils.UnMarshalJsonString(resp, &ret)
		if ret.RetCode != common.DefaultEventMeshRetCode.SUCCESS.RetCode {
			log.Fatalf("Request failed, error code: %d", ret.RetCode)
		}

	}

}

func (e *EventMeshHttpConsumer) Subscribe(topicList []protocol.SubscriptionItem, subscribeUrl string) {

	// FIXME check topicList, subscribeUrl is not blank

	requestParam := e.buildCommonRequestParam()
	requestParam.AddHeader(common.ProtocolKey.REQUEST_CODE, strconv.Itoa(common.DefaultRequestCode.SUBSCRIBE.RequestCode))
	requestParam.AddBody(client.SubscribeRequestBodyKey.TOPIC, gutils.MarshalJsonString(topicList))
	requestParam.AddBody(client.SubscribeRequestBodyKey.URL, subscribeUrl)
	requestParam.AddBody(client.SubscribeRequestBodyKey.CONSUMERGROUP, e.EventMeshHttpClientConfig.ConsumerGroup())

	target := e.SelectEventMesh()
	resp := utils.HttpPost(e.HttpClient, target, requestParam)
	var ret http.EventMeshRetObj
	gutils.UnMarshalJsonString(resp, &ret)
	if ret.RetCode != common.DefaultEventMeshRetCode.SUCCESS.RetCode {
		log.Fatalf("Request failed, error code: %d", ret.RetCode)
	}
	e.subscriptions = append(e.subscriptions, topicList...)
}

func (e *EventMeshHttpConsumer) buildCommonRequestParam() *model.RequestParam {
	param := model.NewRequestParam(nethttp.MethodPost)
	param.AddHeader(common.ProtocolKey.ClientInstanceKey.ENV, e.EventMeshHttpClientConfig.Env())
	param.AddHeader(common.ProtocolKey.ClientInstanceKey.IDC, e.EventMeshHttpClientConfig.Idc())
	param.AddHeader(common.ProtocolKey.ClientInstanceKey.IP, e.EventMeshHttpClientConfig.Ip())
	param.AddHeader(common.ProtocolKey.ClientInstanceKey.PID, e.EventMeshHttpClientConfig.Pid())
	param.AddHeader(common.ProtocolKey.ClientInstanceKey.SYS, e.EventMeshHttpClientConfig.Sys())
	param.AddHeader(common.ProtocolKey.ClientInstanceKey.USERNAME, e.EventMeshHttpClientConfig.UserName())
	param.AddHeader(common.ProtocolKey.ClientInstanceKey.PASSWORD, e.EventMeshHttpClientConfig.Password())
	param.AddHeader(common.ProtocolKey.VERSION, common.DefaultProtocolVersion.V1.Version())
	param.AddHeader(common.ProtocolKey.LANGUAGE, gcommon.Constants.LANGUAGE_GO)
	param.SetTimeout(gcommon.Constants.DEFAULT_HTTP_TIME_OUT)
	param.AddBody(client.HeartbeatRequestBodyKey.CONSUMERGROUP, e.EventMeshHttpClientConfig.ConsumerGroup())
	return param
}
