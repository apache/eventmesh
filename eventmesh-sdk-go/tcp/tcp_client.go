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
	"bufio"
	"bytes"
	"io"
	"math/rand"
	"net"
	"strconv"

	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/protocol/tcp"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/protocol/tcp/codec"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/tcp/common"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/tcp/conf"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/tcp/utils"
)

type BaseTCPClient struct {
	clientNo int
	host     string
	port     int
	useAgent tcp.UserAgent
	conn     net.Conn
}

func NewBaseTCPClient(eventMeshTcpClientConfig conf.EventMeshTCPClientConfig) *BaseTCPClient {
	return &BaseTCPClient{
		clientNo: rand.Intn(10000),
		host:     eventMeshTcpClientConfig.Host(),
		port:     eventMeshTcpClientConfig.Port(),
		useAgent: eventMeshTcpClientConfig.UserAgent(),
	}
}

func (c *BaseTCPClient) Open() {
	eventMeshIpAndPort := c.host + ":" + strconv.Itoa(c.port)
	conn, err := net.Dial("tcp", eventMeshIpAndPort)
	if err != nil {
		log.Errorf("Failed to dial")
	}
	c.conn = conn

	go c.read()
}

func (c *BaseTCPClient) Close() {
	if c.conn != nil {
		err := c.conn.Close()
		if err != nil {
			log.Errorf("Failed to close connection")
		}
		c.Goodbye()
	}
}

func (c *BaseTCPClient) Heartbeat() {
	msg := utils.BuildHeartBeatPackage()
	c.IO(msg, 1000)
}

func (c *BaseTCPClient) Hello() {
	msg := utils.BuildHelloPackage(c.useAgent)
	c.IO(msg, 1000)
}

func (c *BaseTCPClient) Reconnect() {

}

func (c *BaseTCPClient) Goodbye() {

}

func (c *BaseTCPClient) IsActive() {

}

func (c *BaseTCPClient) read() error {
	for {
		var buf bytes.Buffer
		for {
			reader := bufio.NewReader(c.conn)
			msg, isPrefix, err := reader.ReadLine()
			if err != nil {
				if err == io.EOF {
					break
				}
				return err
			}

			buf.Write(msg)
			if !isPrefix {
				break
			}
		}

		go c.handleRead(&buf)
	}
}

func (c *BaseTCPClient) handleRead(in *bytes.Buffer) {
	decoded := codec.DecodePackage(in)
	log.Panicf("Read from server: %v", decoded)
	// TODO Handle according to the command
}

func (c *BaseTCPClient) write(message []byte) (int, error) {
	writer := bufio.NewWriter(c.conn)
	n, err := writer.Write(message)
	if err == nil {
		err = writer.Flush()
	}
	return n, err
}

func (c *BaseTCPClient) Send(message tcp.Package) {
	out := codec.EncodePackage(message)
	_, err := c.write(out.Bytes())
	if err != nil {
		log.Fatalf("Failed to write to peer")
	}
}

func (c *BaseTCPClient) IO(message tcp.Package, timeout int64) tcp.Package {
	key := common.GetRequestContextKey(message)
	ctx := common.NewRequestContext(key, message, 1)
	c.Send(message)
	return ctx.Response()
}
