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
	reflect "reflect"

	consumer "github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/protocol/grpc/consumer"
	gomock "github.com/golang/mock/gomock"
)

// MockConsumerManager is a mock of ConsumerManager interface.
type MockConsumerManager struct {
	ctrl     *gomock.Controller
	recorder *MockConsumerManagerMockRecorder
}

// MockConsumerManagerMockRecorder is the mock recorder for MockConsumerManager.
type MockConsumerManagerMockRecorder struct {
	mock *MockConsumerManager
}

// NewMockConsumerManager creates a new mock instance.
func NewMockConsumerManager(ctrl *gomock.Controller) *MockConsumerManager {
	mock := &MockConsumerManager{ctrl: ctrl}
	mock.recorder = &MockConsumerManagerMockRecorder{mock}
	return mock
}

// EXPECT returns an object that allows the caller to indicate expected use.
func (m *MockConsumerManager) EXPECT() *MockConsumerManagerMockRecorder {
	return m.recorder
}

// DeRegisterClient mocks base method.
func (m *MockConsumerManager) DeRegisterClient(arg0 *consumer.GroupClient) error {
	m.ctrl.T.Helper()
	ret := m.ctrl.Call(m, "DeRegisterClient", arg0)
	ret0, _ := ret[0].(error)
	return ret0
}

// DeRegisterClient indicates an expected call of DeRegisterClient.
func (mr *MockConsumerManagerMockRecorder) DeRegisterClient(arg0 interface{}) *gomock.Call {
	mr.mock.ctrl.T.Helper()
	return mr.mock.ctrl.RecordCallWithMethodType(mr.mock, "DeRegisterClient", reflect.TypeOf((*MockConsumerManager)(nil).DeRegisterClient), arg0)
}

// GetConsumer mocks base method.
func (m *MockConsumerManager) GetConsumer(arg0 string) (consumer.EventMeshConsumer, error) {
	m.ctrl.T.Helper()
	ret := m.ctrl.Call(m, "GetConsumer", arg0)
	ret0, _ := ret[0].(consumer.EventMeshConsumer)
	ret1, _ := ret[1].(error)
	return ret0, ret1
}

// GetConsumer indicates an expected call of GetConsumer.
func (mr *MockConsumerManagerMockRecorder) GetConsumer(arg0 interface{}) *gomock.Call {
	mr.mock.ctrl.T.Helper()
	return mr.mock.ctrl.RecordCallWithMethodType(mr.mock, "GetConsumer", reflect.TypeOf((*MockConsumerManager)(nil).GetConsumer), arg0)
}

// RegisterClient mocks base method.
func (m *MockConsumerManager) RegisterClient(arg0 *consumer.GroupClient) error {
	m.ctrl.T.Helper()
	ret := m.ctrl.Call(m, "RegisterClient", arg0)
	ret0, _ := ret[0].(error)
	return ret0
}

// RegisterClient indicates an expected call of RegisterClient.
func (mr *MockConsumerManagerMockRecorder) RegisterClient(arg0 interface{}) *gomock.Call {
	mr.mock.ctrl.T.Helper()
	return mr.mock.ctrl.RecordCallWithMethodType(mr.mock, "RegisterClient", reflect.TypeOf((*MockConsumerManager)(nil).RegisterClient), arg0)
}

// RestartConsumer mocks base method.
func (m *MockConsumerManager) RestartConsumer(arg0 string) error {
	m.ctrl.T.Helper()
	ret := m.ctrl.Call(m, "RestartConsumer", arg0)
	ret0, _ := ret[0].(error)
	return ret0
}

// RestartConsumer indicates an expected call of RestartConsumer.
func (mr *MockConsumerManagerMockRecorder) RestartConsumer(arg0 interface{}) *gomock.Call {
	mr.mock.ctrl.T.Helper()
	return mr.mock.ctrl.RecordCallWithMethodType(mr.mock, "RestartConsumer", reflect.TypeOf((*MockConsumerManager)(nil).RestartConsumer), arg0)
}

// Start mocks base method.
func (m *MockConsumerManager) Start() error {
	m.ctrl.T.Helper()
	ret := m.ctrl.Call(m, "Start")
	ret0, _ := ret[0].(error)
	return ret0
}

// Start indicates an expected call of Start.
func (mr *MockConsumerManagerMockRecorder) Start() *gomock.Call {
	mr.mock.ctrl.T.Helper()
	return mr.mock.ctrl.RecordCallWithMethodType(mr.mock, "Start", reflect.TypeOf((*MockConsumerManager)(nil).Start))
}

// Stop mocks base method.
func (m *MockConsumerManager) Stop() error {
	m.ctrl.T.Helper()
	ret := m.ctrl.Call(m, "Stop")
	ret0, _ := ret[0].(error)
	return ret0
}

// Stop indicates an expected call of Stop.
func (mr *MockConsumerManagerMockRecorder) Stop() *gomock.Call {
	mr.mock.ctrl.T.Helper()
	return mr.mock.ctrl.RecordCallWithMethodType(mr.mock, "Stop", reflect.TypeOf((*MockConsumerManager)(nil).Stop))
}

// UpdateClientTime mocks base method.
func (m *MockConsumerManager) UpdateClientTime(arg0 *consumer.GroupClient) {
	m.ctrl.T.Helper()
	m.ctrl.Call(m, "UpdateClientTime", arg0)
}

// UpdateClientTime indicates an expected call of UpdateClientTime.
func (mr *MockConsumerManagerMockRecorder) UpdateClientTime(arg0 interface{}) *gomock.Call {
	mr.mock.ctrl.T.Helper()
	return mr.mock.ctrl.RecordCallWithMethodType(mr.mock, "UpdateClientTime", reflect.TypeOf((*MockConsumerManager)(nil).UpdateClientTime), arg0)
}
