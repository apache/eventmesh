/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package id

import (
	"errors"
	"github.com/agiledragon/gomonkey/v2"
	. "github.com/onsi/ginkgo"
	. "github.com/onsi/gomega"
	"github.com/sony/sonyflake"
	"net"
	"reflect"
	"strconv"
	"strings"
)

var _ = Describe("id_snake test", func() {

	Context("NewFlake() exception test ", func() {
		It("should get error", func() {
			mockPatches := gomonkey.ApplyFunc(GetNetInterfaces, func() ([]net.Interface, error) {
				return nil, errors.New("test error")
			})
			want := "flake not created"
			var ret string
			defer func() {
				if err := recover(); err != nil {
					ret = err.(error).Error()
					Ω(ret).To(Equal(want))
				}
				mockPatches.Reset()
			}()

			_ = NewFlake()
		})
	})

	Context("NextID() exception test ", func() {

		It("should get error", func() {
			macAddr := getMacAddr()
			st := sonyflake.Settings{
				MachineID: func() (uint16, error) {
					ma := strings.Split(macAddr, ":")
					mid, err := strconv.ParseInt(ma[0]+ma[1], 16, 16)
					return uint16(mid), err
				},
			}

			aSonyflake := sonyflake.NewSonyflake(st)

			mockPatches := gomonkey.ApplyMethod(reflect.TypeOf(aSonyflake), "NextID", func() (uint64, error) {
				return 0, errors.New("test error")
			})

			flake := NewFlakeWithSonyflake(aSonyflake)

			want := "test error"
			var ret string
			defer func() {
				if err := recover(); err != nil {
					ret = err.(error).Error()
					Ω(want).To(Equal(ret))
				}
				mockPatches.Reset()
			}()

			_ = flake.Next()
		})
	})

})
