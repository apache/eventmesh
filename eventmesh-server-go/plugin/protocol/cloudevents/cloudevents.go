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
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/common/protocol/grpc"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/common/protocol/tcp"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/consts"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
	"github.com/cloudevents/sdk-go/v2"
)

func init() {
	plugin.Register(plugin.Protocol, &CloudeventsPlugin{})
}

// CloudeventsPlugin CloudEvents protocol adaptor
// used to transform CloudEvents message to CloudEvents message.
type CloudeventsPlugin struct {
}

func (c *CloudeventsPlugin) Type() string {
	return "cloudevents"
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
		return c.deserializeGrpcProtocol(sm)
	}
	//TODO implement me
	panic("implement me")
}

func (c *CloudeventsPlugin) ToCloudEvents(i interface{}) ([]*v2.Event, error) {
	//TODO implement me
	panic("implement me")
}

func (c *CloudeventsPlugin) FromCloudEvent(event *v2.Event) (interface{}, error) {
	//TODO implement me
	panic("implement me")
}

func (c *CloudeventsPlugin) ProtocolType() string {
	//TODO implement me
	panic("implement me")
}

func (c *CloudeventsPlugin) deserializeTcpProtocol(pck *tcp.Package) (*v2.Event, error) {
	// TODO add when support tcp procotol
	panic("implement me")
}

func (c *CloudeventsPlugin) deserializeGrpcProtocol(sm *pb.SimpleMessage) (*v2.Event, error) {
	content := sm.Content
	ct, ok := sm.Properties[grpc.CONTENT_TYPE]
	if !ok {
		ct = consts.CONTENT_TYPE_CLOUDEVENTS_JSON
	}

}
