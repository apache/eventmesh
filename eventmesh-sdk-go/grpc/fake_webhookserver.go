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

package grpc

import (
	"context"
	"fmt"
	"io/ioutil"
	"net/http"
)

// runWebhookServer start a webhook server for fake server on
// subscribe topic with webhook
// need to call srv.Shutdown() to close the http.Server gracefully
func runWebhookServer(ctx context.Context) {
	mux := http.NewServeMux()
	mux.HandleFunc("/onmessage", func(writer http.ResponseWriter, request *http.Request) {
		buf, err := ioutil.ReadAll(request.Body)
		if err != nil {
			fmt.Printf("read webhook msg from body, err:%v", err)
			writer.WriteHeader(http.StatusOK)
			writer.Write([]byte("read body err"))
			return
		}
		fmt.Printf("got webhook msg:%s\n", string(buf))
		writer.WriteHeader(http.StatusOK)
		writer.Write([]byte("OK"))
	})
	srv := &http.Server{
		Addr:    ":8080",
		Handler: mux,
	}
	go func() {
		if err := srv.ListenAndServe(); err != nil {
			return
		}
		fmt.Println("http server shutdown")
	}()

	select {
	case <-ctx.Done():
		srv.Shutdown(context.TODO())
	}
}
