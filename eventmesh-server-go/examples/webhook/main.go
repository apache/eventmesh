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

package main

import (
	"flag"
	"fmt"
	"github.com/gin-gonic/gin"
	"io/ioutil"
	"net/http"
	"net/url"
	"strings"
)

// Response indicate the http response
type Response struct {
	RetCode string `json:"retCode"`
	ErrMsg  string `json:"errMsg"`
}

// OK return the success response
func OK(data interface{}) *Response {
	return &Response{
		RetCode: "0",
		ErrMsg:  "OK",
	}
}

// Err return the error response
func Err(err error, code string) *Response {
	return &Response{
		RetCode: code,
		ErrMsg:  err.Error(),
	}
}

// provide a webserver to handle the operator event callback
func main() {
	flag.Parse()
	router := gin.Default()
	printAndReply := func(c *gin.Context) {
		buf, err := ioutil.ReadAll(c.Request.Body)
		if err != nil {
			c.JSON(http.StatusOK, Err(err, "-1"))
			return
		}
		content, err := url.QueryUnescape(string(buf))
		if err != nil {
			c.JSON(http.StatusOK, Err(err, "-1"))
			return
		}

		fmt.Printf("query content: %s \n", contentEscape(content))
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

func contentEscape(content string) string {
	escapedContent := strings.Replace(content, "\n", "", -1)
	escapedContent = strings.Replace(escapedContent, "\r", "", -1)
	return escapedContent
}
