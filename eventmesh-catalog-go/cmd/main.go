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

package main

import (
	"context"
	"errors"
	"github.com/apache/incubator-eventmesh/eventmesh-catalog-go/api"
	"github.com/apache/incubator-eventmesh/eventmesh-catalog-go/api/proto"
	"github.com/apache/incubator-eventmesh/eventmesh-catalog-go/internal/dal"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/database/mysql"
	"github.com/spf13/cobra"
	"io/ioutil"
	"log"
	"os"
	"strings"
)

var (
	rootCmd = &cobra.Command{
		Use:               "catalog",
		Short:             "CLI for catalog",
		Long:              `CLI for eventmesh catalog`,
		PersistentPreRunE: cmdPreRunE,
	}

	createCmd = &cobra.Command{
		Use:   "create",
		Short: "create event catalog",
		Long:  "create event catalog with asyncapi api yaml file",
		RunE:  createWorkflow,
	}
	catalogFileName string
	catalogDSN      string
)

func init() {
	rootCmd.AddCommand(createCmd)
	rootCmd.SetOut(os.Stdout)
	rootCmd.SetErr(os.Stderr)

	createCmd.Flags().StringVarP(&catalogFileName, "file", "",
		"", "catalog file name")
	createCmd.Flags().StringVarP(&catalogDSN, "dsn", "",
		"", "catalog store mysql dsn")
}

func main() {
	if err := rootCmd.Execute(); err != nil {
		log.Fatalf("cmd execute error: %v", err)
	}
}

func cmdPreRunE(cmd *cobra.Command, args []string) error {
	setupConfig()
	if err := dal.Open(); err != nil {
		return err
	}
	return nil
}

func setupConfig() {
	mysql.PluginConfig.DSN = catalogDSN
}

func createWorkflow(cmd *cobra.Command, args []string) error {
	cmd.Println("----begin create event catalog----")
	buf, err := ioutil.ReadFile(catalogFileName)
	if err != nil {
		return err
	}
	t := strings.Split(catalogFileName, "/")
	if len(t) == 0 {
		return errors.New("file name invalid")
	}
	var request = proto.RegistryRequest{}
	request.FileName = t[len(t)-1:][0]
	request.Definition = string(buf)
	var s = api.NewCatalogImpl()
	if _, err = s.Registry(context.Background(), &request); err != nil {
		return err
	}
	cmd.Println("----finish----")
	return nil
}
