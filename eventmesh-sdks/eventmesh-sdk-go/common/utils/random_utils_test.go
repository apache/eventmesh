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

var _ = Describe("random utils test", func() {
	Context("RandomStr()  test ", func() {
		It("should be different string", func() {
			randomMap := make(map[string]struct{})
			for i := 0; i < 16; i++ {
				str := RandomStr(10)
				if _, ok := randomMap[str]; ok {
					Ω(ok).To(Equal(false))
				}
				Ω(len(str)).To(Equal(10))
				randomMap[str] = struct{}{}
			}
		})
	})

	Context("RandomNumberStr()  test ", func() {
		It("should be different number string", func() {
			randomMap := make(map[string]struct{})
			for i := 0; i < 16; i++ {
				str := RandomNumberStr(10)
				if _, ok := randomMap[str]; ok {
					Ω(ok).To(Equal(false))
				}
				Ω(len(str)).To(Equal(10))
				randomMap[str] = struct{}{}
			}
		})
	})

})
