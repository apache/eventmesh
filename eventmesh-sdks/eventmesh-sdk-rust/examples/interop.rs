// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

//! Convert explicitly between EventMesh and OpenMessaging models before
//! sending them through the unified message API.

use eventmesh::message::{EventMeshMessage, Message, OpenMessage};

fn main() -> eventmesh::Result<()> {
    let native = EventMeshMessage::new("orders", "created").with_property("region", "cn");
    let open = Message::from(native).into_open()?;
    let native = Message::from(open).into_event_mesh()?;
    assert_eq!(native.topic.as_deref(), Some("orders"));

    let _open = OpenMessage::new("orders", "created");
    Ok(())
}
