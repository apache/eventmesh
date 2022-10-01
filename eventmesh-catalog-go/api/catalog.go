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

package api

import (
	"context"
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-catalog-go/api/proto"
	"github.com/apache/incubator-eventmesh/eventmesh-catalog-go/internal/constants"
	"github.com/apache/incubator-eventmesh/eventmesh-catalog-go/internal/dal"
	"github.com/apache/incubator-eventmesh/eventmesh-catalog-go/internal/dal/model"
	"github.com/apache/incubator-eventmesh/eventmesh-catalog-go/internal/util"
	"github.com/apache/incubator-eventmesh/eventmesh-catalog-go/pkg/asyncapi"
	v2 "github.com/apache/incubator-eventmesh/eventmesh-catalog-go/pkg/asyncapi/v2"
	"github.com/gogf/gf/util/gconv"
	"gorm.io/gorm"
	"time"
)

type catalogImpl struct {
	proto.UnimplementedCatalogServer
	catalogDAL dal.CatalogDAL
}

func NewCatalogImpl() proto.CatalogServer {
	var c catalogImpl
	c.catalogDAL = dal.NewCatalogDAL()
	return &c
}

func (c *catalogImpl) Registry(ctx context.Context, request *proto.RegistryRequest) (*proto.RegistryResponse, error) {
	var rsp = &proto.RegistryResponse{}
	if len(request.Definition) == 0 {
		return rsp, nil
	}
	var doc = new(v2.Document)
	if err := v2.Decode(gconv.Bytes(request.Definition), &doc); err != nil {
		return rsp, err
	}
	if len(doc.Channels()) == 0 {
		return rsp, nil
	}
	if err := dal.GetDalClient().WithContext(ctx).Transaction(func(tx *gorm.DB) error {
		var handlers []func() error
		for _, channel := range doc.Channels() {
			for _, operation := range channel.Operations() {
				var record = c.buildEventCatalog(request.FileName, channel, operation)
				handlers = append(handlers, func() error {
					return c.catalogDAL.Insert(context.Background(), tx, record)
				})
			}
		}
		handlers = append(handlers, func() error {
			return c.catalogDAL.InsertEvent(context.Background(), tx, c.buildEvent(request.FileName,
				request.Definition, doc))
		})
		return util.GoAndWait(handlers...)
	}); err != nil {
		return rsp, err
	}
	return rsp, nil
}

func (c *catalogImpl) QueryOperations(ctx context.Context, in *proto.QueryOperationsRequest) (*proto.
	QueryOperationsResponse, error) {
	var rsp = &proto.QueryOperationsResponse{}
	res, err := c.catalogDAL.SelectOperations(ctx, in.ServiceName, in.OperationId)
	if err != nil {
		return rsp, err
	}
	if err = gconv.Structs(rsp.Operations, &res); err != nil {
		return nil, err
	}
	return rsp, nil
}

func (c *catalogImpl) buildEventCatalog(fileName string, channel asyncapi.Channel,
	operation asyncapi.Operation) *model.EventCatalog {
	var record model.EventCatalog
	record.OperationID = fmt.Sprintf("file://%s#%s", fileName, operation.ID())
	record.ChannelName = channel.ID()
	record.Type = string(operation.Type())
	record.Status = constants.NormalStatus
	record.CreateTime = time.Now()
	record.UpdateTime = time.Now()
	record.Schema = gconv.String(operation.Messages()[0].Payload())
	return &record
}

func (c *catalogImpl) buildEvent(fileName string, definition string, doc asyncapi.Document) *model.Event {
	var record model.Event
	record.Title = doc.Info().Title()
	record.FileName = fileName
	record.Definition = definition
	record.Status = constants.NormalStatus
	record.Version = doc.Info().Version()
	record.CreateTime = time.Now()
	record.UpdateTime = time.Now()
	return &record
}
