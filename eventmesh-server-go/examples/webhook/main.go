// Copyright (C) @2021 Webank Group Holding Limited
//
// Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
// 	http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under the License
// is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
// or implied. See the License for the specific language governing permissions and limitations under
// the License.
// date: 2021/03/07 15:45
// author: bruceliu@webank.com

package main

import (
	"flag"
	"fmt"
	"io/ioutil"
	"net/http"
	"time"

	"github.com/gin-gonic/gin"
)

// Response indicate the http response
type Response struct {
	Code    int         `json:"code"`
	Msg     string      `json:"msg"`
	TraceID string      `json:"traceId"`
	Data    interface{} `json:"data"`
}

// WithTraceID set the trace id for response
func (r *Response) WithTraceID(traceID string) *Response {
	r.TraceID = traceID
	return r
}

// OK return the success response
func OK(data interface{}) *Response {
	return &Response{
		Code: 0,
		Msg:  "OK",
		Data: data,
	}
}

// Err return the error response
func Err(err error, code int) *Response {
	return &Response{
		Code: code,
		Msg:  err.Error(),
	}
}

var sleepTimeout int64 = 0

func init() {
	flag.Int64Var(&sleepTimeout, "s", 0, "set the sleep timeout")
}

// provide a webserver to handle the operator event callback
func main() {
	flag.Parse()
	router := gin.Default()
	printAndReply := func(c *gin.Context) {
		if sleepTimeout != 0 {
			time.Sleep(time.Second * time.Duration(sleepTimeout))
		}
		buf, err := ioutil.ReadAll(c.Request.Body)
		if err != nil {
			c.JSON(http.StatusOK, Err(err, -1))
			return
		}
		name := c.Param("name")
		version := c.Param("version")
		fmt.Printf("name:%s, version:%s, data:%s\n", name, version, string(buf))
		c.JSON(http.StatusOK, OK("OK"))
	}
	router.Any("/*anypath", func(c *gin.Context) {
		printAndReply(c)
	})

	fmt.Println("server start at:18080")
	// if the web server start failed, we need to stop the server right now
	if err := router.Run(fmt.Sprintf(":%d", 18080)); err != nil {
		panic(err)
	}
}
