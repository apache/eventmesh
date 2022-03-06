package http

import (
	gcommon "eventmesh/common"
	"eventmesh/http/conf"
	nethttp "net/http"
	"time"
)

type AbstractHttpClient struct {
	EventMeshHttpClientConfig conf.EventMeshHttpClientConfig
	HttpClient                *nethttp.Client
}

func NewAbstractHttpClient(eventMeshHttpClientConfig conf.EventMeshHttpClientConfig) *AbstractHttpClient {
	c := &AbstractHttpClient{EventMeshHttpClientConfig: eventMeshHttpClientConfig}
	c.HttpClient = c.SetHttpClient()
	return c
}

func (c *AbstractHttpClient) Close() {
	//	Http Client does not need to close explicitly
}

func (c *AbstractHttpClient) SetHttpClient() *nethttp.Client {
	if !c.EventMeshHttpClientConfig.UseTls() {
		return &nethttp.Client{Timeout: 100 * time.Second}
	}

	// Use TLS
	return &nethttp.Client{Timeout: 100 * time.Second}
}

func (c *AbstractHttpClient) SelectEventMesh() string {
	// FIXME Add load balance support
	uri := c.EventMeshHttpClientConfig.LiteEventMeshAddr()

	if c.EventMeshHttpClientConfig.UseTls() {
		return gcommon.Constants.HTTPS_PROTOCOL_PREFIX + uri
	}

	return gcommon.Constants.HTTP_PROTOCOL_PREFIX + uri
}
