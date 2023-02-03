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
	"github.com/apache/incubator-eventmesh/eventmesh-catalog-go/api/proto"
	"github.com/apache/incubator-eventmesh/eventmesh-catalog-go/internal/dal"
	"github.com/apache/incubator-eventmesh/eventmesh-catalog-go/internal/dal/model"
	"github.com/gogf/gf/util/gconv"
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
	if err := c.catalogDAL.Save(ctx, &model.Event{Definition: request.Definition}); err != nil {
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
	if err = gconv.Structs(res, &rsp.Operations); err != nil {
		return nil, err
	}
	return rsp, nil
}
