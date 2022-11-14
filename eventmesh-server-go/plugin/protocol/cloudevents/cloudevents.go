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

package cloudevents

import (
	"context"
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/common/protocol/grpc"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/common/protocol/tcp"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/protocol"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/consts"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
	"github.com/cloudevents/sdk-go/v2"
	"github.com/cloudevents/sdk-go/v2/event"
	"github.com/cloudevents/sdk-go/v2/event/datacodec"
)

func init() {
	plugin.Register(protocol.CloudEvents, &CloudeventsPlugin{})
}

// CloudeventsPlugin CloudEvents protocol adaptor
// used to transform CloudEvents message to CloudEvents message.
type CloudeventsPlugin struct {
}

func (c *CloudeventsPlugin) Type() string {
	return plugin.Protocol
}

func (c *CloudeventsPlugin) Setup(name string, dec plugin.Decoder) error {
	return nil
}

func (c *CloudeventsPlugin) ToCloudEvent(msg interface{}) (*v2.Event, error) {
	switch msg.(type) {
	case *tcp.Package:
		pck := msg.(*tcp.Package)
		return c.deserializeTcpProtocol(pck)
	case *grpc.SimpleMessageWrapper:
		sw := msg.(*grpc.SimpleMessageWrapper)
		sm := sw.SimpleMessage
		return deserializeGrpcProtocol(sm)
	}
	panic("implement me")
}

func (c *CloudeventsPlugin) ToCloudEvents(msg interface{}) ([]*v2.Event, error) {
	bmw := msg.(*grpc.BatchMessageWrapper)
	return buildBatchMessage(bmw.BatchMessage)
}

func (c *CloudeventsPlugin) FromCloudEvent(event *v2.Event) (interface{}, error) {
	desc := event.Extensions()[grpc.PROTOCOL_DESC].(string)
	if desc == "grpc" {
		return buildSimpleMessage(event)
	}
	return nil, fmt.Errorf("only grpc supported now")
}

func (c *CloudeventsPlugin) ProtocolType() string {
	//TODO implement me
	panic("implement me")
}

func (c *CloudeventsPlugin) deserializeTcpProtocol(pck *tcp.Package) (*v2.Event, error) {
	// TODO add when support tcp procotol
	panic("implement me")
}

func defaultIfEmpty(in string, def interface{}) string {
	if in == "" {
		return def.(string)
	}
	return in
}

func defaultIfNil(def string, in interface{}) string {
	if in == nil {
		return def
	}
	return in.(string)
}

func deserializeGrpcProtocol(sm *pb.SimpleMessage) (*v2.Event, error) {
	content := sm.Content
	ct, ok := sm.Properties[grpc.CONTENT_TYPE]
	if !ok {
		ct = consts.CONTENT_TYPE_CLOUDEVENTS_JSON
	}
	evt := v2.NewEvent()
	if err := datacodec.Decode(context.TODO(), ct, []byte(content), &evt); err != nil {
		return nil, err
	}
	hdr := sm.Header
	result := v2.NewEvent()
	ver := defaultIfEmpty(hdr.ProtocolVersion, evt.Extensions()[grpc.PROTOCOL_VERSION])
	topic := defaultIfEmpty(sm.Topic, evt.Subject())
	result.Extensions()[grpc.ENV] = defaultIfEmpty(hdr.Env, evt.Extensions()[grpc.ENV])
	result.Extensions()[grpc.IDC] = defaultIfEmpty(hdr.Idc, evt.Extensions()[grpc.IDC])
	result.Extensions()[grpc.IP] = defaultIfEmpty(hdr.Ip, evt.Extensions()[grpc.IP])
	result.Extensions()[grpc.PID] = defaultIfEmpty(hdr.Pid, evt.Extensions()[grpc.PID])
	result.Extensions()[grpc.SYS] = defaultIfEmpty(hdr.Sys, evt.Extensions()[grpc.SYS])
	result.Extensions()[grpc.LANGUAGE] = defaultIfEmpty(hdr.Language, evt.Extensions()[grpc.LANGUAGE])
	result.Extensions()[grpc.PROTOCOL_TYPE] = defaultIfEmpty(hdr.ProtocolType, evt.Extensions()[grpc.PROTOCOL_TYPE])
	result.Extensions()[grpc.PROTOCOL_DESC] = defaultIfEmpty(hdr.ProtocolDesc, evt.Extensions()[grpc.PROTOCOL_DESC])
	result.Extensions()[grpc.PROTOCOL_VERSION] = defaultIfEmpty(hdr.ProtocolVersion, evt.Extensions()[grpc.PROTOCOL_VERSION])
	result.Extensions()[grpc.UNIQUE_ID] = defaultIfEmpty(sm.UniqueId, evt.Extensions()[grpc.UNIQUE_ID])
	result.Extensions()[grpc.SEQ_NUM] = defaultIfEmpty(sm.SeqNum, evt.Extensions()[grpc.SEQ_NUM])
	result.Extensions()[grpc.USERNAME] = defaultIfEmpty(hdr.Username, evt.Extensions()[grpc.USERNAME])
	result.Extensions()[grpc.PASSWD] = defaultIfEmpty(hdr.Password, evt.Extensions()[grpc.PASSWD])
	result.Extensions()[grpc.TTL] = defaultIfEmpty(sm.Ttl, evt.Extensions()[grpc.TTL])
	result.Extensions()[grpc.PRODUCERGROUP] = defaultIfEmpty(sm.ProducerGroup, evt.Extensions()[grpc.PRODUCERGROUP])

	if ver == event.CloudEventsVersionV1 {
		result.SetSpecVersion(event.CloudEventsVersionV1)
	} else {
		result.SetSpecVersion(event.CloudEventsVersionV03)
	}
	result.SetSubject(topic)

	return &result, nil
}

func buildBatchMessage(bm *pb.BatchMessage) ([]*v2.Event, error) {
	var msgs []*v2.Event
	hdr := bm.Header

	for _, item := range bm.MessageItem {
		content := item.Content
		ct, ok := item.Properties[grpc.CONTENT_TYPE]
		if !ok {
			ct = consts.CONTENT_TYPE_CLOUDEVENTS_JSON
		}
		evt := v2.NewEvent()
		if err := datacodec.Decode(context.TODO(), ct, []byte(content), &evt); err != nil {
			return nil, err
		}
		result := v2.NewEvent()
		ver := defaultIfEmpty(hdr.ProtocolVersion, evt.Extensions()[grpc.PROTOCOL_VERSION])
		topic := defaultIfEmpty(bm.Topic, evt.Subject())
		result.Extensions()[grpc.ENV] = defaultIfEmpty(hdr.Env, evt.Extensions()[grpc.ENV])
		result.Extensions()[grpc.IDC] = defaultIfEmpty(hdr.Idc, evt.Extensions()[grpc.IDC])
		result.Extensions()[grpc.IP] = defaultIfEmpty(hdr.Ip, evt.Extensions()[grpc.IP])
		result.Extensions()[grpc.PID] = defaultIfEmpty(hdr.Pid, evt.Extensions()[grpc.PID])
		result.Extensions()[grpc.SYS] = defaultIfEmpty(hdr.Sys, evt.Extensions()[grpc.SYS])
		result.Extensions()[grpc.LANGUAGE] = defaultIfEmpty(hdr.Language, evt.Extensions()[grpc.LANGUAGE])
		result.Extensions()[grpc.PROTOCOL_TYPE] = defaultIfEmpty(hdr.ProtocolType, evt.Extensions()[grpc.PROTOCOL_TYPE])
		result.Extensions()[grpc.PROTOCOL_DESC] = defaultIfEmpty(hdr.ProtocolDesc, evt.Extensions()[grpc.PROTOCOL_DESC])
		result.Extensions()[grpc.PROTOCOL_VERSION] = defaultIfEmpty(hdr.ProtocolVersion, evt.Extensions()[grpc.PROTOCOL_VERSION])
		result.Extensions()[grpc.UNIQUE_ID] = defaultIfEmpty(item.UniqueId, evt.Extensions()[grpc.UNIQUE_ID])
		result.Extensions()[grpc.SEQ_NUM] = defaultIfEmpty(item.SeqNum, evt.Extensions()[grpc.SEQ_NUM])
		result.Extensions()[grpc.USERNAME] = defaultIfEmpty(hdr.Username, evt.Extensions()[grpc.USERNAME])
		result.Extensions()[grpc.PASSWD] = defaultIfEmpty(hdr.Password, evt.Extensions()[grpc.PASSWD])
		result.Extensions()[grpc.TTL] = defaultIfEmpty(item.Ttl, evt.Extensions()[grpc.TTL])
		result.Extensions()[grpc.PRODUCERGROUP] = defaultIfEmpty(bm.ProducerGroup, evt.Extensions()[grpc.PRODUCERGROUP])
		if ver == event.CloudEventsVersionV1 {
			result.SetSpecVersion(event.CloudEventsVersionV1)
		} else {
			result.SetSpecVersion(event.CloudEventsVersionV03)
		}
		result.SetSubject(topic)
		msgs = append(msgs, &result)
	}
	return msgs, nil
}

func buildSimpleMessage(evt *v2.Event) (*grpc.SimpleMessageWrapper, error) {
	ct, err := datacodec.Encode(context.TODO(), evt.DataContentType(), evt)
	if err != nil {
		return nil, err
	}
	hdr := &pb.RequestHeader{
		Env:             defaultIfNil("env", evt.Extensions()[grpc.ENV]),
		Idc:             defaultIfNil("idc", evt.Extensions()[grpc.IDC]),
		Ip:              defaultIfNil("127.0.0.1", evt.Extensions()[grpc.IP]),
		Pid:             defaultIfNil("123", evt.Extensions()[grpc.PID]),
		Sys:             defaultIfNil("sys123", evt.Extensions()[grpc.SYS]),
		Username:        defaultIfNil("user", evt.Extensions()[grpc.USERNAME]),
		Password:        defaultIfNil("pass", evt.Extensions()[grpc.PASSWD]),
		Language:        defaultIfNil("JAVA", evt.Extensions()[grpc.LANGUAGE]),
		ProtocolType:    defaultIfNil("protocol", evt.Extensions()[grpc.PROTOCOL_TYPE]),
		ProtocolDesc:    defaultIfNil("protocolDesc", evt.Extensions()[grpc.PROTOCOL_DESC]),
		ProtocolVersion: defaultIfNil("1.0", evt.Extensions()[grpc.PROTOCOL_VERSION]),
	}
	msg := &pb.SimpleMessage{
		Header:        hdr,
		Content:       string(ct),
		ProducerGroup: defaultIfNil("producerGroup", evt.Extensions()[grpc.PRODUCERGROUP]),
		SeqNum:        defaultIfNil("", evt.Extensions()[grpc.SEQ_NUM]),
		UniqueId:      defaultIfNil("", evt.Extensions()[grpc.UNIQUE_ID]),
		Topic:         evt.Subject(),
		Ttl:           defaultIfNil("3000", evt.Extensions()[grpc.TTL]),
		Properties:    map[string]string{grpc.CONTENT_TYPE: evt.DataContentType()},
	}
	for k, v := range evt.Extensions() {
		msg.Properties[k] = defaultIfNil("", v)
	}

	return &grpc.SimpleMessageWrapper{SimpleMessage: msg}, nil
}
