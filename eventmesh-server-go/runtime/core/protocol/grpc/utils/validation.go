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

package utils

import (
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
	"github.com/pkg/errors"
)

// errors to validate the simple msg or header or heartbeat msg
var (
	ErrNoIDC        = errors.New("no idc provided in header")
	ErrNoENV        = errors.New("no env provided in header")
	ErrNoIP         = errors.New("no ip provided in header")
	ErrNoPID        = errors.New("no pid provided in header")
	ErrNoSys        = errors.New("no sys provided in header")
	ErrNoUser       = errors.New("no user provided in header")
	ErrNoPasswd     = errors.New("no passwd provided in header")
	ErrNoLanguage   = errors.New("no language provided in header")
	ErrNoUniqID     = errors.New("no language provided in message")
	ErrNoPGroup     = errors.New("no producer group provided in message")
	ErrNoTopic      = errors.New("no topic provided in message")
	ErrNoContent    = errors.New("no content provided in message")
	ErrNoTTL        = errors.New("no ttl provided in message")
	ErrNoSeqNUM     = errors.New("no seq num provided in message")
	ErrNoURL        = errors.New("no url provided in webhook subscribe")
	ErrNoCGroup     = errors.New("no consumer group provided in subscribe")
	ErrNoSubscribe  = errors.New("no subscribe item provided in subscribe")
	ErrWrongSubType = errors.New("wrong subscribe type provided in subscribe")
)

// ValidateHeader check the request header is validate
func ValidateHeader(header *pb.RequestHeader) error {
	if header.Idc == "" {
		return ErrNoIDC
	}
	if header.Env == "" {
		return ErrNoENV
	}
	if header.Ip == "" {
		return ErrNoIP
	}
	if header.Pid == "" {
		return ErrNoPID
	}
	if header.Sys == "" {
		return ErrNoSys
	}
	if header.Username == "" {
		return ErrNoUser
	}
	if header.Password == "" {
		return ErrNoPasswd
	}
	if header.Language == "" {
		return ErrNoLanguage
	}
	return nil
}

// ValidateMessage check the request msg is validate
func ValidateMessage(msg *pb.SimpleMessage) error {
	if msg.UniqueId == "" {
		return ErrNoUniqID
	}
	if msg.ProducerGroup == "" {
		return ErrNoPGroup
	}
	if msg.Topic == "" {
		return ErrNoTopic
	}
	if msg.Content == "" {
		return ErrNoContent
	}
	if msg.Ttl == "" {
		return ErrNoTTL
	}
	return nil
}

// ValidateBatchMessage check the request batch msg is validate
func ValidateBatchMessage(bmsg *pb.BatchMessage) error {
	if bmsg.Topic == "" {
		return ErrNoTopic
	}
	if bmsg.ProducerGroup == "" {
		return ErrNoPGroup
	}
	for _, m := range bmsg.MessageItem {
		if m.UniqueId == "" {
			return ErrNoUniqID
		}
		if m.SeqNum == "" {
			return ErrNoSeqNUM
		}
		if m.Content == "" {
			return ErrNoContent
		}
		if m.Ttl == "" {
			return ErrNoTTL
		}
	}
	return nil
}

// ValidateSubscription check the subscription is validate
func ValidateSubscription(webhook bool, sub *pb.Subscription) error {
	if webhook && sub.Url == "" {
		return ErrNoCGroup
	}
	if len(sub.SubscriptionItems) == 0 {
		return ErrNoSubscribe
	}
	if sub.ConsumerGroup == "" {
		return ErrNoCGroup
	}
	for _, s := range sub.SubscriptionItems {
		if s.Topic == "" {
			return ErrNoTopic
		}
	}
	return nil
}

// ValidateHeartBeat validate the heartbeat msg
func ValidateHeartBeat(hb *pb.Heartbeat) error {
	if hb.ClientType == pb.Heartbeat_PUB && hb.ProducerGroup == "" {
		return ErrNoPGroup
	}
	if hb.ClientType == pb.Heartbeat_SUB && hb.ConsumerGroup == "" {
		return ErrNoCGroup
	}
	for _, it := range hb.HeartbeatItems {
		if it.Topic == "" {
			return ErrNoTopic
		}
	}
	return nil
}
