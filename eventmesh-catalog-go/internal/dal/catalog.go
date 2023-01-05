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

package dal

import (
	"context"
	"github.com/apache/incubator-eventmesh/eventmesh-catalog-go/internal/dal/model"
	"gorm.io/gorm"
)

type CatalogDAL interface {
	SelectOperations(ctx context.Context, serviceName string, operationID string) ([]*model.EventCatalog, error)
	Insert(ctx context.Context, tx *gorm.DB, record *model.EventCatalog) error
	InsertEvent(ctx context.Context, tx *gorm.DB, record *model.Event) error
}

func NewCatalogDAL() CatalogDAL {
	var w catalogDALImpl
	return &w
}

type catalogDALImpl struct {
}

func (c *catalogDALImpl) SelectOperations(ctx context.Context, serviceName string,
	operationID string) ([]*model.EventCatalog, error) {
	var res []*model.EventCatalog
	var condition = model.EventCatalog{ServiceName: serviceName, OperationID: operationID}
	if err := GetDalClient().WithContext(ctx).Where(&condition).Find(&res).Error; err != nil {
		return nil, err
	}
	return res, nil
}

func (c *catalogDALImpl) Insert(ctx context.Context, tx *gorm.DB, record *model.EventCatalog) error {
	return tx.Create(record).Error
}

func (c *catalogDALImpl) InsertEvent(ctx context.Context, tx *gorm.DB, record *model.Event) error {
	return tx.Create(record).Error
}
