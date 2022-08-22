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

package emserver

// GracefulServer is a Server and Stopper, the stopping
// of which is graceful (whatever that means for the kind
// of server being implemented). It must be able to return
// the address it is configured to listen on so that its
// listener can be paired with it upon graceful restarts.
// The net.Listener that a GracefulServer creates must
// implement the Listener interface for restarts to be
// graceful (assuming the listener is for TCP).
type GracefulServer interface {
	// Serve must start the server loop nearly immediately,
	// or at least not return any errors before the server
	// loop begins. Serve blocks indefinitely, or in other
	// words, until the server is stopped. For UDP-only
	// servers, this method can be a no-op that returns nil.
	Serve() error

	// Stop stops the server. It blocks until the
	// server is completely stopped.
	Stop() error
}
