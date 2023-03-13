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
	"github.com/apache/incubator-eventmesh/eventmesh-catalog-go/internal/dal"
	"github.com/gin-gonic/gin"
	"github.com/gogf/gf/util/gconv"
	"net/http"
)

const (
	eventIDParam = "eventId"
)

// CatalogController workflow controller operations
type CatalogController struct {
	catalogDAL dal.CatalogDAL
}

func NewCatalogController() *CatalogController {
	c := CatalogController{}
	c.catalogDAL = dal.NewCatalogDAL()
	return &c
}

// Save save event catalog data
// @Summary 	 save event catalog
// @Description  save event catalog
// @Tags         Catalog
// @Accept       json
// @Produce      json
// @Param 		 request body SaveEventRequest true "event catalog data"
// @Success      200
// @Failure      400
// @Failure      404
// @Failure      500
// @Router       /catalog [post]
func (c *CatalogController) Save(ctx *gin.Context) {
	request := SaveEventRequest{}
	if err := ctx.ShouldBind(&request); err != nil {
		ctx.JSON(http.StatusBadRequest, err.Error())
		return
	}
	if err := c.catalogDAL.Save(ctx, &request.Event); err != nil {
		ctx.JSON(http.StatusInternalServerError, err.Error())
		return
	}
	ctx.JSON(http.StatusOK, nil)
}

// QueryList query event catalog list
// @Summary 	 query event catalog list
// @Description  query event catalog list
// @Tags         Catalog
// @Accept       json
// @Produce      json
// @Param        page query string false "query page"
// @Param        size query string false "query size"
// @Success      200  {object} QueryEventsResponse
// @Failure      400
// @Failure      404
// @Failure      500
// @Router       /catalog [get]
func (c *CatalogController) QueryList(ctx *gin.Context) {
	request := QueryEventsRequest{}
	if err := ctx.ShouldBind(&request); err != nil {
		ctx.JSON(http.StatusBadRequest, err.Error())
		return
	}
	res, total, err := c.catalogDAL.SelectList(ctx, request.Page, request.Size)
	if err != nil {
		ctx.JSON(http.StatusInternalServerError, err.Error())
		return
	}
	ctx.JSON(http.StatusOK, &QueryEventsResponse{Total: total, Events: res})
}

// QueryDetail query event catalog detail info
// @Summary 	 query event catalog detail info
// @Description  query event catalog detail info
// @Tags         Catalog
// @Accept       json
// @Produce      json
// @Param 		 eventId path string true "event id"
// @Success      200  {object} QueryEventResponse
// @Failure      400
// @Failure      404
// @Failure      500
// @Router       /catalog/{eventId} [get]
func (c *CatalogController) QueryDetail(ctx *gin.Context) {
	eventID := gconv.Int(ctx.Param(eventIDParam))
	res, err := c.catalogDAL.Select(ctx, eventID)
	if err != nil {
		ctx.JSON(http.StatusInternalServerError, err.Error())
		return
	}
	if res == nil {
		ctx.JSON(http.StatusOK, nil)
		return
	}
	ctx.JSON(http.StatusOK, &QueryEventResponse{Event: *res})
	return
}
