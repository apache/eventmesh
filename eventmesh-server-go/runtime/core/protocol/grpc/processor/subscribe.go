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

package processor

import (
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/protocol/grpc/utils"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
)

// SubscribeWebHook process the subscribe request
type SubscribeWebHook struct {
	*Processor
}

// Do process the webhook subscription
func (s *SubscribeWebHook) Do(sub *pb.Subscription) error {
	hdr := sub.Header
	if err := utils.ValidateHeader(hdr); err != nil {
		return err
	}
	if err := utils.ValidateSubscription(true, sub); err != nil {
		return err
	}
	if err := s.Interceptor(sub); err != nil {
		return err
	}

	return nil
}
