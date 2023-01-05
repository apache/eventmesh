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

type Event struct {
	ID         int       `json:"id" gorm:"column:id;type:int;primaryKey;autoIncrement"`
	Title      string    `json:"title" gorm:"column:title;type:varchar;size:1024"`
	FileName   string    `json:"file_name" gorm:"column:file_name;type:varchar;size:1024"`
	Definition string    `json:"definition" gorm:"column:definition;type:text;"`
	Status     int       `json:"status" gorm:"column:status;type:int"`
	Version    string    `json:"version" gorm:"column:version;type:varchar;size:64"`
	CreateTime time.Time `json:"create_time"`
	UpdateTime time.Time `json:"update_time"`
}

func (w Event) TableName() string {
	return "t_event"
}
