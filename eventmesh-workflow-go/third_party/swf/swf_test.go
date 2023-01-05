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

package swf

import (
	"testing"

	"github.com/gogf/gf/util/gconv"
	"github.com/serverlessworkflow/sdk-go/v2/model"
	"github.com/stretchr/testify/assert"
)

func Test_Parse(t *testing.T) {
	var source = "id: storeorderworkflow\nversion: '1.0'\nspecVersion: '0.8'\nname: Store Order Management Workflow\nstart: Receive New Order Event\nstates:\n  - name: Receive New Order Event\n    type: event\n    onEvents:\n      - eventRefs:\n          - NewOrderEvent\n        actions:\n          - functionRef:\n              refName: \"OrderServiceSendEvent\"\n    transition: Check New Order Result\n  - name: Check New Order Result\n    type: switch\n    dataConditions:\n      - name: New Order Successfull\n        condition: \"${{ .order.order_no != '' }}\"\n        transition: Send Order Payment\n      - name: New Order Failed\n        condition: \"${{ .order.order_no == '' }}\"\n        end: true\n    defaultCondition:\n      end: true\n  - name: Send Order Payment\n    type: operation\n    actions:\n      - functionRef:\n          refName: \"PaymentServiceSendEvent\"\n    transition: Check Payment Status\n  - name: Check Payment Status\n    type: switch\n    dataConditions:\n      - name: Payment Successfull\n        condition: \"${{ .payment.order_no != '' }}\"\n        transition: Send Order Shipment\n      - name: Payment Denied\n        condition: \"${{ .payment.order_no == '' }}\"\n        end: true\n    defaultCondition:\n      end: true\n  - name: Send Order Shipment\n    type: operation\n    actions:\n      - functionRef:\n          refName: \"ShipmentServiceSendEvent\"\n    end: true\nevents:\n  - name: NewOrderEvent\n    source: store/order\n    type: online.store.newOrder\nfunctions:\n  - name: OrderServiceSendEvent\n    operation: file://orderService.yaml#sendOrder\n    type: asyncapi\n  - name: PaymentServiceSendEvent\n    operation: file://paymentService.yaml#sendPayment\n    type: asyncapi\n  - name: ShipmentServiceSendEvent\n    operation: file://shipmentService.yaml#sendShipment\n    type: asyncapi\n"
	wf, err := Parse(source)
	t.Log(gconv.String(wf))
	assert.Nil(t, err)
	assert.False(t, wf.States[1].(*model.DataBasedSwitchState).DefaultCondition.End.Terminate)
}
