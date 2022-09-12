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
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin"
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

func (c *CloudeventsPlugin) ToCloudEvent(i interface{}) (*v2.Event, error) {
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
