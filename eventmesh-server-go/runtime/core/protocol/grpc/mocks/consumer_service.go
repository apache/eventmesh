/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mocks

import (
	context "context"
	reflect "reflect"

	pb "github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
	gomock "github.com/golang/mock/gomock"
)

// MockConsumerServiceServer is a mock of ConsumerServiceServer interface.
type MockConsumerServiceServer struct {
	ctrl     *gomock.Controller
	recorder *MockConsumerServiceServerMockRecorder
}

// MockConsumerServiceServerMockRecorder is the mock recorder for MockConsumerServiceServer.
type MockConsumerServiceServerMockRecorder struct {
	mock *MockConsumerServiceServer
}

// NewMockConsumerServiceServer creates a new mock instance.
func NewMockConsumerServiceServer(ctrl *gomock.Controller) *MockConsumerServiceServer {
	mock := &MockConsumerServiceServer{ctrl: ctrl}
	mock.recorder = &MockConsumerServiceServerMockRecorder{mock}
	return mock
}

// EXPECT returns an object that allows the caller to indicate expected use.
func (m *MockConsumerServiceServer) EXPECT() *MockConsumerServiceServerMockRecorder {
	return m.recorder
}

// Subscribe mocks base method.
func (m *MockConsumerServiceServer) Subscribe(arg0 context.Context, arg1 *pb.Subscription) (*pb.Response, error) {
	m.ctrl.T.Helper()
	ret := m.ctrl.Call(m, "Subscribe", arg0, arg1)
	ret0, _ := ret[0].(*pb.Response)
	ret1, _ := ret[1].(error)
	return ret0, ret1
}

// Subscribe indicates an expected call of Subscribe.
func (mr *MockConsumerServiceServerMockRecorder) Subscribe(arg0, arg1 interface{}) *gomock.Call {
	mr.mock.ctrl.T.Helper()
	return mr.mock.ctrl.RecordCallWithMethodType(mr.mock, "Subscribe", reflect.TypeOf((*MockConsumerServiceServer)(nil).Subscribe), arg0, arg1)
}

// SubscribeStream mocks base method.
func (m *MockConsumerServiceServer) SubscribeStream(arg0 pb.ConsumerService_SubscribeStreamServer) error {
	m.ctrl.T.Helper()
	ret := m.ctrl.Call(m, "SubscribeStream", arg0)
	ret0, _ := ret[0].(error)
	return ret0
}

// SubscribeStream indicates an expected call of SubscribeStream.
func (mr *MockConsumerServiceServerMockRecorder) SubscribeStream(arg0 interface{}) *gomock.Call {
	mr.mock.ctrl.T.Helper()
	return mr.mock.ctrl.RecordCallWithMethodType(mr.mock, "SubscribeStream", reflect.TypeOf((*MockConsumerServiceServer)(nil).SubscribeStream), arg0)
}

// Unsubscribe mocks base method.
func (m *MockConsumerServiceServer) Unsubscribe(arg0 context.Context, arg1 *pb.Subscription) (*pb.Response, error) {
	m.ctrl.T.Helper()
	ret := m.ctrl.Call(m, "Unsubscribe", arg0, arg1)
	ret0, _ := ret[0].(*pb.Response)
	ret1, _ := ret[1].(error)
	return ret0, ret1
}

// Unsubscribe indicates an expected call of Unsubscribe.
func (mr *MockConsumerServiceServerMockRecorder) Unsubscribe(arg0, arg1 interface{}) *gomock.Call {
	mr.mock.ctrl.T.Helper()
	return mr.mock.ctrl.RecordCallWithMethodType(mr.mock, "Unsubscribe", reflect.TypeOf((*MockConsumerServiceServer)(nil).Unsubscribe), arg0, arg1)
}

// mustEmbedUnimplementedConsumerServiceServer mocks base method.
func (m *MockConsumerServiceServer) mustEmbedUnimplementedConsumerServiceServer() {
	m.ctrl.T.Helper()
	m.ctrl.Call(m, "mustEmbedUnimplementedConsumerServiceServer")
}

// mustEmbedUnimplementedConsumerServiceServer indicates an expected call of mustEmbedUnimplementedConsumerServiceServer.
func (mr *MockConsumerServiceServerMockRecorder) mustEmbedUnimplementedConsumerServiceServer() *gomock.Call {
	mr.mock.ctrl.T.Helper()
	return mr.mock.ctrl.RecordCallWithMethodType(mr.mock, "mustEmbedUnimplementedConsumerServiceServer", reflect.TypeOf((*MockConsumerServiceServer)(nil).mustEmbedUnimplementedConsumerServiceServer))
}
