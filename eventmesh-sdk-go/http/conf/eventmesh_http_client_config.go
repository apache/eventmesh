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

package conf

type EventMeshHttpClientConfig struct {
	//The event server address list
	//	If it's a cluster, please use ; to split, and the address format is related to loadBalanceType.
	//	E.g.
	//	If you use Random strategy, the format like: 127.0.0.1:10105;127.0.0.2:10105
	//	If you use weighted round robin or weighted random strategy, the format like: 127.0.0.1:10105:1;127.0.0.2:10105:2
	liteEventMeshAddr string
	// TODO support load balance
	//loadBalanceType string
	consumeThreadCore int
	consumeThreadMax  int
	env               string
	consumerGroup     string
	producerGroup     string
	idc               string
	ip                string
	pid               string
	sys               string
	userName          string
	password          string
	useTls            bool
}

func (e *EventMeshHttpClientConfig) LiteEventMeshAddr() string {
	return e.liteEventMeshAddr
}

func (e *EventMeshHttpClientConfig) SetLiteEventMeshAddr(liteEventMeshAddr string) {
	e.liteEventMeshAddr = liteEventMeshAddr
}

func (e *EventMeshHttpClientConfig) ConsumeThreadCore() int {
	return e.consumeThreadCore
}

func (e *EventMeshHttpClientConfig) SetConsumeThreadCore(consumeThreadCore int) {
	e.consumeThreadCore = consumeThreadCore
}

func (e *EventMeshHttpClientConfig) ConsumeThreadMax() int {
	return e.consumeThreadMax
}

func (e *EventMeshHttpClientConfig) SetConsumeThreadMax(consumeThreadMax int) {
	e.consumeThreadMax = consumeThreadMax
}

func (e *EventMeshHttpClientConfig) Env() string {
	return e.env
}

func (e *EventMeshHttpClientConfig) SetEnv(env string) {
	e.env = env
}

func (e *EventMeshHttpClientConfig) ConsumerGroup() string {
	return e.consumerGroup
}

func (e *EventMeshHttpClientConfig) SetConsumerGroup(consumerGroup string) {
	e.consumerGroup = consumerGroup
}

func (e *EventMeshHttpClientConfig) ProducerGroup() string {
	return e.producerGroup
}

func (e *EventMeshHttpClientConfig) SetProducerGroup(producerGroup string) {
	e.producerGroup = producerGroup
}

func (e *EventMeshHttpClientConfig) Idc() string {
	return e.idc
}

func (e *EventMeshHttpClientConfig) SetIdc(idc string) {
	e.idc = idc
}

func (e *EventMeshHttpClientConfig) Ip() string {
	return e.ip
}

func (e *EventMeshHttpClientConfig) SetIp(ip string) {
	e.ip = ip
}

func (e *EventMeshHttpClientConfig) Pid() string {
	return e.pid
}

func (e *EventMeshHttpClientConfig) SetPid(pid string) {
	e.pid = pid
}

func (e *EventMeshHttpClientConfig) Sys() string {
	return e.sys
}

func (e *EventMeshHttpClientConfig) SetSys(sys string) {
	e.sys = sys
}

func (e *EventMeshHttpClientConfig) UserName() string {
	return e.userName
}

func (e *EventMeshHttpClientConfig) SetUserName(userName string) {
	e.userName = userName
}

func (e *EventMeshHttpClientConfig) Password() string {
	return e.password
}

func (e *EventMeshHttpClientConfig) SetPassword(password string) {
	e.password = password
}

func (e *EventMeshHttpClientConfig) UseTls() bool {
	return e.useTls
}

func (e *EventMeshHttpClientConfig) SetUseTls(useTls bool) {
	e.useTls = useTls
}

var DefaultEventMeshHttpClientConfig = EventMeshHttpClientConfig{
	liteEventMeshAddr: "127.0.0.1:10105",
	consumeThreadCore: 2,
	consumeThreadMax:  5,
	env:               "",
	consumerGroup:     "DefaultConsumerGroup",
	producerGroup:     "DefaultProducerGroup",
	idc:               "",
	ip:                "",
	pid:               "",
	sys:               "",
	userName:          "",
	password:          "",
	useTls:            false,
}
