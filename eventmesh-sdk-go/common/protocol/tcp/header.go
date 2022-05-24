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

package tcp

import (
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/utils"
)

type Header struct {
	Cmd        Command                `json:"cmd"`
	Code       int                    `json:"code"`
	Desc       string                 `json:"desc"`
	Seq        string                 `json:"seq"`
	Properties map[string]interface{} `json:"properties"`
}

func (h Header) PutProperty(name string, value interface{}) {
	h.Properties[name] = value
}

func (h Header) GetProperty(name string) interface{} {
	if h.Properties == nil {
		return nil
	}

	if val, ok := h.Properties[name]; ok {
		return val
	}

	return nil
}

func (h Header) Marshal() []byte {
	newHeader := make(map[string]interface{})
	newHeader["cmd"] = h.Cmd
	// Compatible with Java Enum serialization
	newHeader["command"] = h.Cmd
	newHeader["code"] = h.Code
	newHeader["desc"] = h.Desc
	newHeader["seq"] = h.Seq
	newHeader["properties"] = h.Properties
	return utils.MarshalJsonBytes(newHeader)
}

func (h Header) getVal(key string, headerDict map[string]interface{}) interface{} {
	if val, ok := headerDict[key]; ok {
		return val
	}
	return nil
}

func (h Header) Unmarshal(header []byte) Header {

	var headerDict map[string]interface{}
	utils.UnMarshalJsonBytes(header, &headerDict)

	if val := h.getVal("cmd", headerDict); val != nil {
		h.Cmd = Command(val.(string))
	}

	if val := h.getVal("code", headerDict); val != nil {
		h.Code = int(val.(float64))
	}

	if val := h.getVal("desc", headerDict); val != nil {
		h.Desc = val.(string)
	}

	if val := h.getVal("seq", headerDict); val != nil {
		h.Seq = val.(string)
	}

	if val := h.getVal("properties", headerDict); val != nil {
		h.Properties = val.(map[string]interface{})
	}

	return h
}

func NewHeader(cmd Command, code int, desc string, seq string) Header {
	return Header{Cmd: cmd, Code: code, Desc: desc, Seq: seq, Properties: map[string]interface{}{}}
}
