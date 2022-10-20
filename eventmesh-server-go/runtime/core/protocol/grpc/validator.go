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

package grpc

import (
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
)

var (
	ErrHeaderNoIDC    = fmt.Errorf("no idc found in header")
	ErrHeaderNoENV    = fmt.Errorf("no env found in header")
	ErrHeaderNoIP     = fmt.Errorf("no ip found in header")
	ErrHeaderNoPID    = fmt.Errorf("no pid found in header")
	ErrHeaderNoSYS    = fmt.Errorf("no sys found in header")
	ErrHeaderNoUser   = fmt.Errorf("no username found in header")
	ErrHeaderNoPASSWD = fmt.Errorf("no passwd found in header")
	ErrHeaderNoLANG   = fmt.Errorf("no language found in header")

	ErrMessageNoUID     = fmt.Errorf("no uid found in message")
	ErrMessageNoPG      = fmt.Errorf("no producer group found in message")
	ErrMessageNoTopic   = fmt.Errorf("no topic found in message")
	ErrMessageNoContent = fmt.Errorf("no content found in message")
	ErrMessageNoTTL     = fmt.Errorf("no ttl found in message")

	ErrSubscriptionNoURL     = fmt.Errorf("no subscription url on webhook type")
	ErrSubscriptionNoCG      = fmt.Errorf("no subscription consumer group on grpc type")
	ErrSubscriptionWrongType = fmt.Errorf("wrong subscription type on grpc type")
	ErrSubscriptionNoItem    = fmt.Errorf("no items subscription on grpc type")
	ErrSubscriptionWrongMode = fmt.Errorf("wrong subscription mode on grpc type")
)

func ValidateHeader(hdr *pb.RequestHeader) error {
	if hdr.Idc == "" {
		return ErrHeaderNoIDC
	}
	if hdr.Ip == "" {
		return ErrHeaderNoIP
	}
	if hdr.Env == "" {
		return ErrHeaderNoENV
	}
	if hdr.Pid == "" {
		return ErrHeaderNoPID
	}
	if hdr.Sys == "" {
		return ErrHeaderNoSYS
	}
	if hdr.Username == "" {
		return ErrHeaderNoUser
	}
	if hdr.Password == "" {
		return ErrHeaderNoPASSWD
	}
	if hdr.Language == "" {
		return ErrHeaderNoLANG
	}
	return nil
}

func ValidateMessage(msg *pb.SimpleMessage) error {
	if msg.UniqueId == "" {
		return ErrMessageNoUID
	}
	if msg.ProducerGroup == "" {
		return ErrMessageNoPG
	}
	if msg.Topic == "" {
		return ErrMessageNoTopic
	}
	if msg.Content == "" {
		return ErrMessageNoContent
	}
	if msg.Ttl == "" {
		return ErrMessageNoTTL
	}
	return nil
}

func ValidateSubscription(stype GRPCType, msg *pb.Subscription) error {
	if stype == WEBHOOK && msg.Url == "" {
		return ErrSubscriptionNoURL
	}

	if len(msg.SubscriptionItems) == 0 {
		return ErrSubscriptionNoItem
	}
	return nil
}
