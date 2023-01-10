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
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-catalog-go/internal/constants"
	"github.com/apache/incubator-eventmesh/eventmesh-catalog-go/internal/dal/model"
	"github.com/apache/incubator-eventmesh/eventmesh-catalog-go/internal/util"
	"github.com/apache/incubator-eventmesh/eventmesh-catalog-go/pkg/asyncapi"
	v2 "github.com/apache/incubator-eventmesh/eventmesh-catalog-go/pkg/asyncapi/v2"
	"github.com/gogf/gf/util/gconv"
	"gorm.io/gorm"
	"time"
)

const maxSize = 100

type CatalogDAL interface {
	Select(ctx context.Context, eventID int) (*model.Event, error)
	SelectList(ctx context.Context, page int, size int) ([]model.Event, int, error)
	Save(ctx context.Context, record *model.Event) error
	SelectOperations(ctx context.Context, serviceName string, operationID string) ([]*model.EventCatalog, error)
}

func NewCatalogDAL() CatalogDAL {
	var w catalogDALImpl
	return &w
}

type catalogDALImpl struct {
}

func (c *catalogDALImpl) Select(ctx context.Context, eventID int) (*model.Event, error) {
	var condition = model.Event{ID: eventID}
	var r model.Event
	if err := catalogDB.WithContext(ctx).Where(&condition).First(&r).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, nil
		}
		return nil, err
	}
	return &r, nil
}

func (c *catalogDALImpl) SelectList(ctx context.Context, page int, size int) ([]model.Event, int, error) {
	var r []model.Event
	db := catalogDB.WithContext(ctx).Where("1=1")
	db = db.Where("status = ?", constants.NormalStatus)
	if size > maxSize {
		size = maxSize
	}
	if page == 0 {
		page = 1
	}
	var count int64
	db = db.Limit(size).Offset(size * (page - 1)).Order("update_time DESC")
	if err := db.Find(&r).Limit(-1).Offset(-1).Count(&count).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, 0, nil
		}
		return nil, 0, err
	}
	return r, int(count), nil

}

func (c *catalogDALImpl) SelectOperations(ctx context.Context, serviceName string,
	operationID string) ([]*model.EventCatalog, error) {
	var res []*model.EventCatalog
	var condition = model.EventCatalog{ServiceName: serviceName, OperationID: operationID}
	if err := catalogDB.WithContext(ctx).Where(&condition).Find(&res).Error; err != nil {
		return nil, err
	}
	return res, nil
}

func (c *catalogDALImpl) Save(ctx context.Context, record *model.Event) error {
	return catalogDB.WithContext(ctx).Transaction(func(tx *gorm.DB) error {
		if record.ID > 0 {
			if err := c.delete(tx, record); err != nil {
				return err
			}
		}
		return c.create(tx, record)
	})
}

func (c *catalogDALImpl) insert(tx *gorm.DB, record *model.EventCatalog) error {
	return tx.Create(record).Error
}

func (c *catalogDALImpl) insertEvent(tx *gorm.DB, record *model.Event) error {
	return tx.Create(record).Error
}

func (c *catalogDALImpl) delete(tx *gorm.DB, record *model.Event) error {
	var handlers []func() error
	handlers = append(handlers, func() error {
		cond := model.Event{Status: constants.InvalidStatus, UpdateTime: time.Now()}
		return tx.Where("id = ?", record.ID).Updates(&cond).Error
	}, func() error {
		cond := model.EventCatalog{Status: constants.InvalidStatus, UpdateTime: time.Now()}
		return tx.Where("service_name = ?", record.Title).Updates(&cond).Error
	})
	return util.GoAndWait(handlers...)
}

func (c *catalogDALImpl) create(tx *gorm.DB, record *model.Event) error {
	if len(record.Definition) == 0 {
		return nil
	}
	var doc = new(v2.Document)
	if err := v2.Decode(gconv.Bytes(record.Definition), &doc); err != nil {
		return err
	}
	if len(doc.Channels()) == 0 {
		return nil
	}
	var handlers []func() error
	handlers = append(handlers, func() error {
		return c.insertEvent(tx, c.buildEvent(record.Definition, doc))
	})
	for _, channel := range doc.Channels() {
		for _, operation := range channel.Operations() {
			var eventCatalog = c.buildEventCatalog(doc.Info().Title(), channel, operation)
			handlers = append(handlers, func() error {
				return c.insert(tx, eventCatalog)
			})
		}
	}
	return util.GoAndWait(handlers...)
}

func (c *catalogDALImpl) buildEventCatalog(serviceName string, channel asyncapi.Channel,
	operation asyncapi.Operation) *model.EventCatalog {
	var record model.EventCatalog
	record.ServiceName = serviceName
	record.OperationID = fmt.Sprintf("file://%s.yaml#%s", serviceName, operation.ID())
	record.ChannelName = channel.ID()
	record.Type = string(operation.Type())
	record.Status = constants.NormalStatus
	record.CreateTime = time.Now()
	record.UpdateTime = time.Now()
	record.Schema = gconv.String(operation.Messages()[0].Payload())
	return &record
}

func (c *catalogDALImpl) buildEvent(definition string, doc asyncapi.Document) *model.Event {
	var record model.Event
	record.Title = doc.Info().Title()
	record.FileName = fmt.Sprintf("%s.yaml", record.Title)
	record.Definition = definition
	record.Status = constants.NormalStatus
	record.Version = doc.Info().Version()
	record.CreateTime = time.Now()
	record.UpdateTime = time.Now()
	return &record
}
