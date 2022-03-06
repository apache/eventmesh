package tcp

import (
	"bufio"
	"bytes"
	"eventmesh/common/protocol/tcp"
	"eventmesh/common/protocol/tcp/codec"
	"eventmesh/tcp/common"
	"eventmesh/tcp/conf"
	"eventmesh/tcp/utils"
	"io"
	"log"
	"math/rand"
	"net"
	"strconv"
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
		log.Fatal("Failed to dial")
	}
	c.conn = conn

	go c.read()
}

func (c *BaseTCPClient) Close() {
	if c.conn != nil {
		err := c.conn.Close()
		if err != nil {
			log.Fatal("Failed to close connection")
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
	log.Printf("Read from server: %v\n", decoded)
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
		log.Fatal("Failed to write to peer")
	}
}

func (c *BaseTCPClient) IO(message tcp.Package, timeout int64) tcp.Package {
	key := common.GetRequestContextKey(message)
	ctx := common.NewRequestContext(key, message, 1)
	c.Send(message)
	return ctx.Response()
}
