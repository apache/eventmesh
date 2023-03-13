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

package producer

import (
	"context"
	"encoding/json"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/common/protocol/grpc"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
	v2 "github.com/cloudevents/sdk-go/v2"
	"github.com/cloudevents/sdk-go/v2/event/datacodec"
	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"testing"
)

func Test_createEvents(t *testing.T) {
	data := map[string]string{
		"msg": "msg from grpc go server",
	}
	ct := "application/json"
	evt := v2.NewEvent()
	evt.SetID(uuid.New().String())
	evt.SetSubject("grpc-topic")
	evt.SetSource("/")
	evt.SetDataContentType(ct)
	evt.SetType("cloudevents")
	assert.NoError(t, evt.SetData(ct, data))
	evt.SetExtension(grpc.ENV, "")
	evt.SetExtension(grpc.IDC, "idc-test")
	evt.SetExtension(grpc.IP, "1.1.1.1")
	evt.SetExtension(grpc.PID, "1")
	evt.SetExtension(grpc.SYS, "test")
	evt.SetExtension(grpc.LANGUAGE, "GO")
	evt.SetExtension(grpc.PROTOCOL_TYPE, "cloudevents")
	evt.SetExtension(grpc.PROTOCOL_DESC, "grpc")
	evt.SetExtension(grpc.PROTOCOL_VERSION, evt.SpecVersion())
	evt.SetExtension(grpc.UNIQUE_ID, "1")
	evt.SetExtension(grpc.SEQ_NUM, "1")
	evt.SetExtension(grpc.USERNAME, "USERNAME")
	evt.SetExtension(grpc.PASSWD, "passwd")
	evt.SetExtension(grpc.PRODUCERGROUP, "test-producer-group")
	jsonBody, err := datacodec.Encode(context.TODO(), ct, evt)
	assert.NoError(t, err)
	simpleMsg := &pb.SimpleMessage{
		Header: &pb.RequestHeader{
			Env:             grpc.ENV,
			Idc:             "idc-test",
			Ip:              "1.1.1.1",
			Pid:             "1",
			Sys:             "test",
			Language:        "GO",
			ProtocolType:    "cloudevents",
			ProtocolDesc:    "grpc",
			ProtocolVersion: evt.SpecVersion(),
			Username:        "USERNAME",
			Password:        "passwd",
		},
		ProducerGroup: "test-producer-group",
		Topic:         "grpc-topic",
		Ttl:           "10000",
		SeqNum:        "1",
		UniqueId:      "1",
		Content:       string(jsonBody),
		Properties:    map[string]string{"contenttype": ct},
	}
	for k, v := range evt.Extensions() {
		simpleMsg.Properties[k] = v.(string)
	}
	msgBody, err := json.Marshal(simpleMsg)
	assert.NoError(t, err)
	t.Log(string(msgBody))
}
