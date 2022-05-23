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

package codec

import (
	"bytes"
	"encoding/binary"

	gcommon "github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/protocol/tcp"
	gutils "github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/utils"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/tcp/common"
)

const (
	MAGIC       = "EventMesh"
	VERSION     = "0000"
	LENGTH_SIZE = 4
)

func EncodePackage(message tcp.Package) *bytes.Buffer {

	header := message.Header
	headerData := header.Marshal()

	var bodyData []byte
	if header.GetProperty(gcommon.Constants.PROTOCOL_TYPE) != common.EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME {
		bodyData = gutils.MarshalJsonBytes(message.Body)
	} else {
		bodyData = (message.Body).([]byte)
	}

	headerLen := len(headerData)
	bodyLen := len(bodyData)

	length := LENGTH_SIZE + LENGTH_SIZE + headerLen + bodyLen

	var out bytes.Buffer
	out.WriteString(MAGIC)
	out.WriteString(VERSION)

	lengthBytes := make([]byte, LENGTH_SIZE)
	binary.BigEndian.PutUint32(lengthBytes, uint32(length))

	headerLenBytes := make([]byte, LENGTH_SIZE)
	binary.BigEndian.PutUint32(headerLenBytes, uint32(headerLen))

	out.Write(lengthBytes)
	out.Write(headerLenBytes)
	out.Write(headerData)
	out.Write(bodyData)

	return &out
}

func DecodePackage(in *bytes.Buffer) tcp.Package {
	flagBytes := parseFlag(in)
	versionBytes := parseVersion(in)
	validateFlag(flagBytes, versionBytes)

	length := parseLength(in)
	headerLen := parseLength(in)
	bodyLen := length - headerLen - LENGTH_SIZE - LENGTH_SIZE
	header := parseHeader(in, int(headerLen))
	body := parseBody(in, header, int(bodyLen))
	return tcp.Package{Header: header, Body: body}
}

func parseFlag(in *bytes.Buffer) []byte {
	flagLen := len([]byte(MAGIC))
	flagBytes := make([]byte, flagLen)
	n, err := in.Read(flagBytes)
	if err != nil {
		return nil
	}
	log.Infof("read %d bytes (flag) ", n)
	return flagBytes
}

func parseVersion(in *bytes.Buffer) []byte {
	verLen := len([]byte(VERSION))
	verBytes := make([]byte, verLen)
	n, err := in.Read(verBytes)
	if err != nil {
		return nil
	}
	log.Infof("read %d bytes (version) ", n)
	return verBytes
}

func parseLength(in *bytes.Buffer) uint32 {
	lenBytes := make([]byte, 4)
	n, err := in.Read(lenBytes)
	if err != nil {
		log.Errorf("Failed to parse length")
	}
	log.Infof("read %d bytes (length) ", n)
	return binary.BigEndian.Uint32(lenBytes)
}

func parseHeader(in *bytes.Buffer, headerLen int) tcp.Header {
	headerBytes := make([]byte, headerLen)
	n, err := in.Read(headerBytes)
	if err != nil {
		log.Errorf("Failed to parse header")
	}
	log.Infof("read %d bytes (header) ", n)

	var header tcp.Header
	return header.Unmarshal(headerBytes)
}

func parseBody(in *bytes.Buffer, header tcp.Header, bodyLen int) interface{} {
	if bodyLen <= 0 {
		return nil
	}

	bodyBytes := make([]byte, bodyLen)
	n, err := in.Read(bodyBytes)
	if err != nil {
		log.Errorf("Failed to parse body")
	}
	log.Infof("read %d bytes (body) ", n)

	bodyStr := string(bodyBytes)
	return deserializeBody(bodyStr, header)
}

func deserializeBody(bodyStr string, header tcp.Header) interface{} {
	command := header.Cmd
	switch command {
	case tcp.DefaultCommand.HELLO_REQUEST:
	case tcp.DefaultCommand.RECOMMEND_REQUEST:
		var useAgent tcp.UserAgent
		gutils.UnMarshalJsonString(bodyStr, &useAgent)
		return useAgent
	case tcp.DefaultCommand.SUBSCRIBE_REQUEST:
	case tcp.DefaultCommand.UNSUBSCRIBE_REQUEST:
		return nil
		//return OBJECT_MAPPER.readValue(bodyJsonString, Subscription.class);
	case tcp.DefaultCommand.REQUEST_TO_SERVER:
	case tcp.DefaultCommand.RESPONSE_TO_SERVER:
	case tcp.DefaultCommand.ASYNC_MESSAGE_TO_SERVER:
	case tcp.DefaultCommand.BROADCAST_MESSAGE_TO_SERVER:
	case tcp.DefaultCommand.REQUEST_TO_CLIENT:
	case tcp.DefaultCommand.RESPONSE_TO_CLIENT:
	case tcp.DefaultCommand.ASYNC_MESSAGE_TO_CLIENT:
	case tcp.DefaultCommand.BROADCAST_MESSAGE_TO_CLIENT:
	case tcp.DefaultCommand.REQUEST_TO_CLIENT_ACK:
	case tcp.DefaultCommand.RESPONSE_TO_CLIENT_ACK:
	case tcp.DefaultCommand.ASYNC_MESSAGE_TO_CLIENT_ACK:
	case tcp.DefaultCommand.BROADCAST_MESSAGE_TO_CLIENT_ACK:
		// The message string will be deserialized by protocol plugin, if the event is cloudevents, the body is
		// just a string.
		return bodyStr
	case tcp.DefaultCommand.REDIRECT_TO_CLIENT:
		return nil
		//return OBJECT_MAPPER.readValue(bodyJsonString, RedirectInfo.class);
	default:
		// FIXME improve codes
		log.Errorf("Invalidate TCP command: %s", command)
		return nil
	}

	return nil
}

func validateFlag(flagBytes, versionBytes []byte) {
	// TODO add check
}
