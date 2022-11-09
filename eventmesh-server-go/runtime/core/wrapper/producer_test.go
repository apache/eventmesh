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

package wrapper

import (
	"context"
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/connector"
	eventv2 "github.com/cloudevents/sdk-go/v2"
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestProducer_Send(t *testing.T) {
	type fields struct {
		Base              *Base
		ProducerConnector connector.Producer
	}
	type args struct {
		ctx      context.Context
		event    *eventv2.Event
		callback *connector.SendCallback
	}
	factory := plugin.Get(plugin.Connector, "standalone").(connector.Factory)
	produ, err := factory.GetProducer()
	assert.NoError(t, err)
	tests := []struct {
		name    string
		fields  fields
		args    args
		wantErr assert.ErrorAssertionFunc
	}{
		{
			name: "test send",
			fields: fields{
				Base:              DefaultBaseWrapper(),
				ProducerConnector: produ,
			},
			args: args{
				ctx:   context.TODO(),
				event: &eventv2.Event{},
				callback: &connector.SendCallback{
					OnSuccess: func(result *connector.SendResult) {
						t.Logf("success")
					},
					OnError: func(result *connector.ErrorResult) {
						t.Logf("error")
					},
				},
			},
			wantErr: func(t assert.TestingT, err error, i ...interface{}) bool {
				return err != nil
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			c := &Producer{
				Base:              tt.fields.Base,
				ProducerConnector: tt.fields.ProducerConnector,
			}
			tt.wantErr(t, c.Send(tt.args.ctx, tt.args.event, tt.args.callback), fmt.Sprintf("Send(%v, %v, %v)", tt.args.ctx, tt.args.event, tt.args.callback))
		})
	}
}
