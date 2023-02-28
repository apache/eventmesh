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

package consumer

import (
	config2 "github.com/apache/incubator-eventmesh/eventmesh-server-go/config"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/consts"
	"github.com/liyue201/gostl/ds/set"
	"github.com/pkg/errors"
	"sync"
	"time"
)

var (
	ErrNoConsumerClient = errors.New("no consumer group client")
)

//go:generate mockgen -destination ./mocks/consumer_manager.go -package mocks github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/protocol/grpc/consumer ConsumerManager
type ConsumerManager interface {
	GetConsumer(consumerGroup string) (EventMeshConsumer, error)
	RegisterClient(cli *GroupClient) error
	DeRegisterClient(cli *GroupClient) error
	UpdateClientTime(cli *GroupClient)
	RestartConsumer(consumerGroup string) error
	Start() error
	Stop() error
}

type consumerManager struct {
	// consumerClients store all consumer clients
	// key is consumer group, value is set *GroupClient
	consumerGroupClients *sync.Map

	// consumers eventmesh consumer instances
	// key is consumer group, value is EventMeshConsumer
	consumers *sync.Map
}

// NewConsumerManager create new consumer manager
func NewConsumerManager() (ConsumerManager, error) {
	return &consumerManager{
		consumers:            new(sync.Map),
		consumerGroupClients: new(sync.Map),
	}, nil
}

func (c *consumerManager) GetConsumer(consumerGroup string) (EventMeshConsumer, error) {
	val, ok := c.consumers.Load(consumerGroup)
	if ok {
		return val.(EventMeshConsumer), nil
	}
	cu, err := NewEventMeshConsumer(consumerGroup)
	if err != nil {
		return nil, err
	}
	c.consumers.Store(consumerGroup, cu)
	return cu, nil
}

func (c *consumerManager) RegisterClient(cli *GroupClient) error {
	val, ok := c.consumerGroupClients.Load(cli.ConsumerGroup)
	if !ok {
		cliset := set.New(set.WithGoroutineSafe())
		cliset.Insert(cli)
		c.consumerGroupClients.Store(cli.ConsumerGroup, cliset)
		return nil
	}
	localClients := val.(*set.Set)
	found := false
	for iter := localClients.Begin(); iter.IsValid(); iter.Next() {
		lc := iter.Value().(*GroupClient)
		if lc.GRPCType == consts.WEBHOOK {
			lc.URL = cli.URL
			lc.LastUPTime = cli.LastUPTime
			found = true
			break
		}
		if lc.GRPCType == consts.STREAM {
			lc.Emiter = cli.Emiter
			lc.LastUPTime = cli.LastUPTime
			found = true
			break
		}
	}
	if !found {
		localClients.Insert(cli)
	}
	return nil
}

func (c *consumerManager) DeRegisterClient(cli *GroupClient) error {
	val, ok := c.consumerGroupClients.Load(cli.ConsumerGroup)
	if !ok {
		log.Debugf("no consumer group client found, name:%v", cli.ConsumerGroup)
		return nil
	}
	localClients := val.(*set.Set)
	for iter := localClients.Begin(); iter.IsValid(); iter.Next() {
		lc := iter.Value().(*GroupClient)
		if lc.Topic == cli.Topic {
			if lc.GRPCType == consts.STREAM {
				// TODO
				// close the GRPC client stream before removing it
			}
			localClients.Erase(lc)
		}
	}
	if localClients.Size() == 0 {
		c.consumerGroupClients.Delete(cli.ConsumerGroup)
	}
	return nil
}

func (c *consumerManager) RestartConsumer(consumerGroup string) error {
	val, ok := c.consumers.Load(consumerGroup)
	if !ok {
		return nil
	}
	emconsumer := val.(EventMeshConsumer)
	if emconsumer.ServiceState() == consts.RUNNING {
		if err := emconsumer.Shutdown(); err != nil {
			return err
		}
	}
	if err := emconsumer.Init(); err != nil {
		return err
	}
	if err := emconsumer.Start(); err != nil {
		return err
	}
	if emconsumer.ServiceState() != consts.RUNNING {
		log.Warnf("restart eventmesh consumer failed, status:%v", emconsumer.ServiceState)
		c.consumers.Delete(consumerGroup)
	}
	return nil
}

func (c *consumerManager) UpdateClientTime(cli *GroupClient) {
	val, ok := c.consumerGroupClients.Load(cli.ConsumerGroup)
	if !ok {
		log.Debugf("no consumer group client found, name:%v", cli.ConsumerGroup)
		return
	}
	localClients := val.(*set.Set)
	for iter := localClients.Begin(); iter.IsValid(); iter.Next() {
		iter.Value().(*GroupClient).LastUPTime = time.Now()
	}
}

func (c *consumerManager) clientCheck() {
	sessionExpiredInMills := config2.GlobalConfig().Server.GRPCOption.SessionExpiredInMills
	tk := time.NewTicker(sessionExpiredInMills)
	go func() {
		for range tk.C {
			var consumerGroupRestart []string
			c.consumerGroupClients.Range(func(key, value any) bool {
				localClients := value.(*set.Set)
				for iter := localClients.Begin(); iter.IsValid(); iter.Next() {
					lc := iter.Value().(*GroupClient)
					if time.Now().Sub(lc.LastUPTime) > sessionExpiredInMills {
						log.Warnf("client:%v lastUpdate time:%v over three heartbeat cycles. Removing it",
							lc.ConsumerGroup, lc.LastUPTime)
						emconsumer, err := c.GetConsumer(lc.ConsumerGroup)
						if err != nil {
							log.Warnf("get eventmesh consumer:%v failed, err:%v", lc.ConsumerGroup, err)
							return true
						}
						if err := c.DeRegisterClient(lc); err != nil {
							log.Warnf("deregistry client:%v err:%v", lc.ConsumerGroup, err)
							return true
						}
						if ok := emconsumer.DeRegisterClient(lc); !ok {
							log.Warnf("failed deregistry client:%v in eventmesh consumer", lc.ConsumerGroup)
							return true
						}
						consumerGroupRestart = append(consumerGroupRestart, lc.ConsumerGroup)
					}
				}
				for _, rs := range consumerGroupRestart {
					if err := c.RestartConsumer(rs); err != nil {
						log.Warnf("deregistry consumer:%v  err:%v", rs, err)
						return true
					}
				}
				return true
			})
		}
	}()
}

func (c *consumerManager) Start() error {
	log.Infof("start consumer manager")
	return nil
}

func (c *consumerManager) Stop() error {
	return nil
}
