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

package utils

import (
	. "github.com/onsi/ginkgo"
	. "github.com/onsi/gomega"
)

var _ = Describe("json utils test", func() {
	Context("MarshalJsonBytes()  test ", func() {
		It("should not be nil", func() {
			json_bytes := MarshalJsonBytes("test")
			Ω(json_bytes).To(Not(BeNil()))
		})

		It("should not be nil", func() {
			json_bytes := MarshalJsonBytes(nil)
			Ω(json_bytes).To(Not(BeNil()))
		})
	})

	Context("UnMarshalJsonBytes()  test ", func() {
		It("should be equals source string", func() {
			json_bytes := MarshalJsonBytes("test")
			var test_str string
			UnMarshalJsonBytes(json_bytes, &test_str)
			Ω(test_str).To(Equal("test"))
		})
	})

	Context("MarshalJsonString()  test ", func() {
		It("should not be nil", func() {
			json_bytes := MarshalJsonString("test")
			Ω(json_bytes).To(Not(BeNil()))
		})

		It("should not be nil", func() {
			json_bytes := MarshalJsonString(nil)
			Ω(json_bytes).To(Not(BeNil()))
		})
	})

	Context("UnMarshalJsonString()  test ", func() {
		It("should be equals source string", func() {
			json_bytes := MarshalJsonString("test")
			var test_str string
			UnMarshalJsonString(json_bytes, &test_str)
			Ω(test_str).To(Equal("test"))
		})
	})

})
