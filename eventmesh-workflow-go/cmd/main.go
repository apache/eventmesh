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
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/database/mysql"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/dal"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/dal/model"
	"github.com/spf13/cobra"
	"io/ioutil"
	"log"
	"os"
)

var (
	rootCmd = &cobra.Command{
		Use:               "workflow",
		Short:             "CLI for workflow",
		Long:              `CLI for eventmesh workflow`,
		PersistentPreRunE: cmdPreRunE,
	}

	createCmd = &cobra.Command{
		Use:   "create",
		Short: "create workflow",
		Long:  "create workflow with serverless workflow yaml file",
		RunE:  createWorkflow,
	}
	workflowFileName string
	workflowDSN      string
)

func init() {
	rootCmd.AddCommand(createCmd)
	rootCmd.SetOut(os.Stdout)
	rootCmd.SetErr(os.Stderr)

	createCmd.Flags().StringVarP(&workflowFileName, "file", "",
		"", "workflow file name")
	createCmd.Flags().StringVarP(&workflowDSN, "dsn", "",
		"", "workflow store mysql dsn")
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
	//plugin.Register(constants.LogSchedule, log.DefaultLogFactory)
	//plugin.Register(constants.LogQueue, log.DefaultLogFactory)
	mysql.PluginConfig.DSN = workflowDSN
}

func createWorkflow(cmd *cobra.Command, args []string) error {
	cmd.Println("----begin create workflow----")
	buf, err := ioutil.ReadFile(workflowFileName)
	if err != nil {
		return err
	}
	var wf = model.Workflow{}
	wf.Definition = string(buf)
	if err = dal.NewWorkflowDAL().Insert(context.Background(), &wf); err != nil {
		return err
	}
	cmd.Println("----finish----")
	return nil
}
