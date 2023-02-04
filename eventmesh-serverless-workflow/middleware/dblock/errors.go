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

package dblock

import "errors"

// ErrGetLockContextCancelled is returned when user given context is cancelled while trying to obtain the lock
var ErrGetLockContextCancelled = errors.New("context cancelled while trying to obtain lock")

// ErrMySQLTimeout is returned when the MySQL server can't acquire the lock in the specified timeout
var ErrMySQLTimeout = errors.New("(mysql) timeout while acquiring the lock")

// ErrMySQLInternalError is returned when MySQL is returning a generic internal error
var ErrMySQLInternalError = errors.New("internal mysql error acquiring the lock")
