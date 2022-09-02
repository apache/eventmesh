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

package model

import (
	"time"
)

type EventCatalog struct {
	ID          int       `json:"id" gorm:"column:id;type:int;primaryKey;autoIncrement"`
	OperationID string    `json:"operation_id" gorm:"column:operation_id;type:varchar;size:1024"`
	ChannelName string    `json:"channel_name" gorm:"column:channel_name;type:varchar;size:1024"`
	Type        string    `json:"type" gorm:"column:type;type:string"`
	Schema      string    `json:"schema" gorm:"column:schema;type:text;"`
	Status      int       `json:"status" gorm:"column:status;type:int"`
	CreateTime  time.Time `json:"create_time"`
	UpdateTime  time.Time `json:"update_time"`
}

func (w EventCatalog) TableName() string {
	return "t_event_catalog"
}
