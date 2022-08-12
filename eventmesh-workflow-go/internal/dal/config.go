package dal

import (
	"database/sql"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/middleware/dblock"
	"gorm.io/gorm/logger"
	"time"

	"gorm.io/driver/mysql"
	"gorm.io/gorm"

	pmysql "github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/database/mysql"
)

var workflowDB *gorm.DB
var workflowLock *dblock.MysqlLocker

func Open() error {
	var err error
	d, err := sql.Open("mysql", pmysql.PluginConfig.DSN)
	d.SetMaxOpenConns(pmysql.PluginConfig.MaxOpen)
	d.SetMaxIdleConns(pmysql.PluginConfig.MaxIdle)
	d.SetConnMaxLifetime(time.Millisecond * time.Duration(pmysql.PluginConfig.MaxLifetime))

	workflowDB, err = gorm.Open(mysql.New(mysql.Config{Conn: d}),
		&gorm.Config{Logger: logger.Default.LogMode(logger.Info)})
	if err != nil {
		return err
	}
	db, err := workflowDB.DB()
	if err != nil {
		return err
	}
	workflowLock = dblock.NewMysqlLocker(db)
	return nil
}

func GetDalClient() *gorm.DB {
	return workflowDB
}

func GetLockClient() *dblock.MysqlLocker {
	return workflowLock
}
