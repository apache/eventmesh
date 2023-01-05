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

package asyncapi

// Decoder decodes an AsyncAPI document (several formats can be supported).
// See https://github.com/asyncapi/parser-go#overview for the minimum supported schemas.
type Decoder interface {
	Decode([]byte, interface{}) error
}

// DecodeFunc is a helper func that implements the Decoder interface.
type DecodeFunc func([]byte, interface{}) error

func (d DecodeFunc) Decode(b []byte, dst interface{}) error {
	return d(b, dst)
}
