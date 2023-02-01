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
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/dal"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/dal/model"
	"github.com/gin-gonic/gin"
	"net/http"
)

const (
	workflowIDParam = "workflowId"
)

// WorkflowController workflow controller operations
type WorkflowController struct {
	workflowDAL dal.WorkflowDAL
}

func NewWorkflowController() *WorkflowController {
	c := WorkflowController{}
	c.workflowDAL = dal.NewWorkflowDAL()
	return &c
}

// Save save a workflow
// @Summary 	 save a workflow
// @Description  save a workflow
// @Tags         workflow
// @Accept       json
// @Produce      json
// @Param 		 request body SaveWorkflowRequest true "workflow data"
// @Success      200
// @Failure      400
// @Failure      404
// @Failure      500
// @Router       /workflow [post]
func (c *WorkflowController) Save(ctx *gin.Context) {
	request := SaveWorkflowRequest{}
	if err := ctx.ShouldBind(&request); err != nil {
		ctx.JSON(http.StatusBadRequest, err.Error())
		return
	}
	if err := c.workflowDAL.Save(ctx, &request.Workflow); err != nil {
		ctx.JSON(http.StatusInternalServerError, err.Error())
		return
	}
	ctx.JSON(http.StatusOK, nil)
}

// QueryList query workflow list
// @Summary 	 query workflow list
// @Description  query workflow list
// @Tags         workflow
// @Accept       json
// @Produce      json
// @Param      	 workflow_id query string false "workflow id"
// @Param      	 status query string false "workflow status"
// @Param        page query string false "query page"
// @Param        size query string false "query size"
// @Success      200  {object} QueryWorkflowsResponse
// @Failure      400
// @Failure      404
// @Failure      500
// @Router       /workflow [get]
func (c *WorkflowController) QueryList(ctx *gin.Context) {
	request := QueryWorkflowsRequest{}
	if err := ctx.ShouldBind(&request); err != nil {
		ctx.JSON(http.StatusBadRequest, err.Error())
		return
	}
	res, total, err := c.workflowDAL.SelectList(ctx, &model.QueryParam{
		WorkflowID: request.WorkflowID,
		Status:     request.Status,
		Page:       request.Page,
		Size:       request.Size,
	})
	if err != nil {
		ctx.JSON(http.StatusInternalServerError, err.Error())
		return
	}
	ctx.JSON(http.StatusOK, &QueryWorkflowsResponse{Total: total, Workflows: res})
}

// QueryDetail query workflow detail info
// @Summary 	 query workflow detail info
// @Description  query workflow detail info
// @Tags         workflow
// @Accept       json
// @Produce      json
// @Param 		 workflowId path string true "workflow id"
// @Success      200  {object} QueryWorkflowResponse
// @Failure      400
// @Failure      404
// @Failure      500
// @Router       /workflow/{workflowId} [get]
func (c *WorkflowController) QueryDetail(ctx *gin.Context) {
	workflowID := ctx.Param(workflowIDParam)
	res, err := c.workflowDAL.Select(ctx, dal.GetDalClient(), workflowID)
	if err != nil {
		ctx.JSON(http.StatusInternalServerError, err.Error())
		return
	}
	if res == nil {
		ctx.JSON(http.StatusOK, nil)
		return
	}
	ctx.JSON(http.StatusOK, &QueryWorkflowResponse{Workflow: *res})
	return
}

// Delete delete a workflow
// @Summary 	delete a workflow
// @Description  delete a workflow
// @Tags         workflow
// @Accept       json
// @Produce      json
// @Param 		 workflowId path string true "workflow id"
// @Success      200  {object} QueryWorkflowsResponse
// @Failure      400
// @Failure      404
// @Failure      500
// @Router       /workflow/{workflowId} [delete]
func (c *WorkflowController) Delete(ctx *gin.Context) {
	workflowID := ctx.Param(workflowIDParam)
	if err := c.workflowDAL.Delete(ctx, workflowID); err != nil {
		ctx.JSON(http.StatusInternalServerError, err.Error())
		return
	}
	ctx.JSON(http.StatusOK, nil)
}

// QueryInstances query workflow instances
// @Summary 	query workflow instances
// @Description  query workflow instances
// @Tags         workflow
// @Accept       json
// @Produce      json
// @Param      	 workflow_id query string false "workflow id"
// @Param        page query string false "query page"
// @Param        size query string false "query size"
// @Success      200  {object} QueryWorkflowInstancesResponse
// @Failure      400
// @Failure      404
// @Failure      500
// @Router       /workflow/instances [get]
func (c *WorkflowController) QueryInstances(ctx *gin.Context) {
	request := QueryWorkflowInstancesRequest{}
	if err := ctx.ShouldBind(&request); err != nil {
		ctx.JSON(http.StatusBadRequest, err.Error())
		return
	}
	res, total, err := c.workflowDAL.SelectInstances(ctx, &model.QueryParam{
		WorkflowID: request.WorkflowID,
		Page:       request.Page,
		Size:       request.Size,
	})
	if err != nil {
		ctx.JSON(http.StatusInternalServerError, err.Error())
		return
	}
	ctx.JSON(http.StatusOK, &QueryWorkflowInstancesResponse{Total: total, WorkflowInstances: res})
}
