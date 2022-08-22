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

package middleware

import (
	"context"
	"github.com/stretchr/testify/assert"
	"testing"
)

func Test_middleware(t *testing.T) {
	m1 := func(hdl Handler) Handler {
		return func(ctx context.Context, i interface{}) (interface{}, error) {
			t.Logf("in handler, in:%v", i)
			return nil, nil
		}
	}

	val, err := Chain([]Middleware{m1}...)(func(ctx context.Context, i interface{}) (interface{}, error) {
		return "here", nil
	})(context.TODO(), "from")
	assert.NoError(t, err)
	t.Logf("%v", val)
}
