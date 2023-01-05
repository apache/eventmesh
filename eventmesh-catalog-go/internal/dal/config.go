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
	"database/sql"
	"gorm.io/gorm/logger"
	"time"

	"gorm.io/driver/mysql"
	"gorm.io/gorm"

	pmysql "github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/database/mysql"
)

var catalogDB *gorm.DB

func Open() error {
	var err error
	d, err := sql.Open("mysql", pmysql.PluginConfig.DSN)
	d.SetMaxOpenConns(pmysql.PluginConfig.MaxOpen)
	d.SetMaxIdleConns(pmysql.PluginConfig.MaxIdle)
	d.SetConnMaxLifetime(time.Millisecond * time.Duration(pmysql.PluginConfig.MaxLifetime))

	catalogDB, err = gorm.Open(mysql.New(mysql.Config{Conn: d}),
		&gorm.Config{Logger: logger.Default.LogMode(logger.Silent)})
	if err != nil {
		return err
	}
	return nil
}

func GetDalClient() *gorm.DB {
	return catalogDB
}
