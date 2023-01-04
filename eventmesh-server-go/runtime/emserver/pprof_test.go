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

package emserver

import (
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/config"
	"github.com/stretchr/testify/assert"
	"io"
	"net/http"
	"testing"
)

func Test_pprof(t *testing.T) {
	srv := NewPProfServer(&config.PProfOption{
		Enable: true,
		Port:   "8080",
	})
	assert.NotNil(t, srv)
	go srv.Serve()
	resp, err := http.Get("http://localhost:8080/debug/pprof")
	assert.NoError(t, err)
	assert.Equal(t, http.StatusOK, resp.StatusCode)
	buf, err := io.ReadAll(resp.Body)
	assert.NoError(t, err)
	assert.NotNil(t, buf)
	assert.True(t, len(buf) > 0)
	t.Log(string(buf))
	assert.NoError(t, srv.Stop())
}
