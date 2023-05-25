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

package seq

import (
	. "github.com/onsi/ginkgo"
	. "github.com/onsi/gomega"
)

var _ = Describe("id_snake test", func() {

	var z Interface
	BeforeEach(func() {
		z = NewAtomicSeq()
	})

	Context("NewAtomicSeq()() test ", func() {
		It("should not be null", func() {
			Ω(z).To(Not(BeNil()))
		})
	})

	Context("Next() test ", func() {

		It("should no error and increase", func() {
			s := z.Next()
			Ω(s).To(Equal("1"))
			s = z.Next()
			Ω(s).To(Equal("2"))
		})
	})

})
