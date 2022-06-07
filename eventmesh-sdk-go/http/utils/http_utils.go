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
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/http/model"
	"io/ioutil"
	nethttp "net/http"
	"net/url"
	"strings"
)

func HttpPost(client *nethttp.Client, uri string, requestParam *model.RequestParam) string {

	data := url.Values{}
	body := requestParam.Body()
	for key := range body {
		data.Set(key, body[key])
	}

	req, err := nethttp.NewRequest(nethttp.MethodPost, uri, strings.NewReader(data.Encode()))
	if err != nil {
	}

	req.Header.Set("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")

	headers := requestParam.Headers()
	for header := range headers {
		req.Header[header] = []string{headers[header]}
	}

	resp, err := client.Do(req)
	if err != nil {
	}

	defer resp.Body.Close()

	respBody, err := ioutil.ReadAll(resp.Body)
	if err != nil {
	}

	return string(respBody)
}
