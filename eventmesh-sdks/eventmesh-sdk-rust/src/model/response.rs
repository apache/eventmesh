/*
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

use std::fmt::{Display, Formatter};

use serde::Deserialize;

#[derive(Debug, Deserialize, Default)]
pub struct EventMeshResponse {
    #[serde(rename = "respCode")]
    resp_code: Option<String>,

    #[serde(rename = "respMsg")]
    resp_msg: Option<String>,

    #[serde(rename = "respTime")]
    resp_time: Option<i64>,
}

impl EventMeshResponse {
    pub fn new(
        resp_code: Option<String>,
        resp_msg: Option<String>,
        resp_time: Option<i64>,
    ) -> Self {
        Self {
            resp_code,
            resp_msg,
            resp_time,
        }
    }
}

impl Display for EventMeshResponse {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        write!(f, "EventMeshResponse[")?;
        if let Some(ref code) = self.resp_code {
            write!(f, "code={code},")?;
        }
        if let Some(ref msg) = self.resp_msg {
            write!(f, "message={msg},")?;
        }
        if let Some(time) = self.resp_time {
            write!(f, "response time={time},")?;
        }
        write!(f, "]")?;
        Ok(())
    }
}
