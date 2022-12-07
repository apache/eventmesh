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
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/connector"
	_ "github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/connector/standalone"
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestConsumer_Subscribe(t *testing.T) {
	type fields struct {
		Base              *Base
		consumerConnector connector.Consumer
	}
	type args struct {
		topicName string
	}
	factory := plugin.Get(plugin.Connector, "standalone").(connector.Factory)
	consu, err := factory.GetConsumer()
	assert.NoError(t, err)
	tests := []struct {
		name    string
		fields  fields
		args    args
		wantErr bool
	}{
		{
			name: "test for subscribe",
			fields: fields{
				Base:              DefaultBaseWrapper(),
				consumerConnector: consu,
			},
			args: args{
				topicName: "subscribe topic",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			c := &Consumer{
				Base:              tt.fields.Base,
				consumerConnector: tt.fields.consumerConnector,
			}
			if err := c.Subscribe(tt.args.topicName); (err != nil) != tt.wantErr {
				t.Errorf("Subscribe() error = %v, wantErr %v", err, tt.wantErr)
			}
		})
	}
}

func TestConsumer_UnSubscribe(t *testing.T) {
	type fields struct {
		Base              *Base
		consumerConnector connector.Consumer
	}
	type args struct {
		topicName string
	}
	factory := plugin.Get(plugin.Connector, "standalone").(connector.Factory)
	consu, err := factory.GetConsumer()
	assert.NoError(t, err)
	tests := []struct {
		name    string
		fields  fields
		args    args
		wantErr assert.ErrorAssertionFunc
	}{
		{
			name: "test unsubscribe",
			fields: fields{
				Base:              DefaultBaseWrapper(),
				consumerConnector: consu,
			},
			args: args{
				topicName: "test_unsubscribe",
			},
			wantErr: func(t assert.TestingT, err error, i ...interface{}) bool {
				return false
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			c := &Consumer{
				Base:              tt.fields.Base,
				consumerConnector: tt.fields.consumerConnector,
			}
			tt.wantErr(t, c.UnSubscribe(tt.args.topicName), fmt.Sprintf("UnSubscribe(%v)", tt.args.topicName))
		})
	}
}
