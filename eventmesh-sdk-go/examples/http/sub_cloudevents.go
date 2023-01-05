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

package http

import (
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/protocol"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/common/utils"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/http/conf"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/http/consumer"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/log"

	cloudevents "github.com/cloudevents/sdk-go/v2"
	"net/http"
	"os"
	"strconv"
	"strings"
)

func SubCloudEvents() {
	eventMeshIPPort := "127.0.0.1" + ":" + "10105"
	consumerGroup := "EventMeshTest-consumerGroup"
	topic := "TEST-TOPIC-HTTP-ASYNC"
	env := "P"
	idc := "FT"
	subSys := "1234"
	// FIXME Get ip dynamically
	localIp := "127.0.0.1"
	localPort := 8090

	subscribeUrl := "http://" + localIp + ":" + strconv.Itoa(localPort) + "/hello"
	topicList := []protocol.SubscriptionItem{
		{
			Topic: topic,
			Mode:  protocol.DefaultSubscriptionMode.CLUSTERING,
			Type:  protocol.DefaultSubscriptionType.ASYNC,
		},
	}

	// Callback handle
	exit := make(chan bool)
	go httpServer(localIp, localPort, exit)

	// (Deep) Copy of default config
	eventMeshClientConfig := conf.DefaultEventMeshHttpClientConfig
	eventMeshClientConfig.SetLiteEventMeshAddr(eventMeshIPPort)
	eventMeshClientConfig.SetConsumerGroup(consumerGroup)
	eventMeshClientConfig.SetEnv(env)
	eventMeshClientConfig.SetIdc(idc)
	eventMeshClientConfig.SetSys(subSys)
	eventMeshClientConfig.SetIp(localIp)
	eventMeshClientConfig.SetPid(strconv.Itoa(os.Getpid()))

	// Subscribe
	eventMeshHttpConsumer := consumer.NewEventMeshHttpConsumer(eventMeshClientConfig)
	eventMeshHttpConsumer.Subscribe(topicList, subscribeUrl)
	eventMeshHttpConsumer.HeartBeat(topicList, subscribeUrl)

	// Wait for exit
	<-exit
}

func httpServer(ip string, port int, exit chan<- bool) {
	http.HandleFunc("/hello", hello)
	err := http.ListenAndServe(ip+":"+strconv.Itoa(port), nil)
	if err != nil {
		log.Fatalf("Failed to launch a callback http server, error: %v", err)
	}

	exit <- true
}

func hello(w http.ResponseWriter, r *http.Request) {
	if r.URL.Path != "/hello" {
		http.NotFound(w, r)
		return
	}

	switch r.Method {
	case "POST":
		contentType := r.Header.Get("Content-Type")

		// FIXME Now we only support post form
		if strings.Contains(contentType, "application/x-www-form-urlencoded") {
			err := r.ParseForm()
			if err != nil {
				log.Errorf("Failed to parse post form parameter, error: %v", err)
			}
			content := r.FormValue("content")
			event := cloudevents.NewEvent()
			utils.UnMarshalJsonString(content, &event)
			log.Infof("Received data from eventmesh server: %v", string(event.Data()))
			return
		}

		w.WriteHeader(http.StatusUnsupportedMediaType)
	default:
		w.WriteHeader(http.StatusNotImplemented)
		w.Write([]byte(http.StatusText(http.StatusNotImplemented)))
	}
}
