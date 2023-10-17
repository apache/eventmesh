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

#![cfg(feature = "eventmesh_message")]

use std::collections::HashMap;
use std::fmt;
use std::time::{SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};

use crate::common::eventmesh_message_utils::EventMeshCloudEventUtils;
use crate::model::convert::FromPbCloudEvent;
use crate::proto_cloud_event::PbCloudEvent;

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct EventMeshMessage {
    #[serde(rename = "bizSeqNo")]
    pub(crate) biz_seq_no: Option<String>,
    #[serde(rename = "uniqueId")]
    pub(crate) unique_id: Option<String>,
    pub(crate) topic: Option<String>,
    pub(crate) content: Option<String>,
    pub(crate) prop: HashMap<String, String>,
    #[serde(rename = "createTime")]
    pub(crate) create_time: u64,
}

impl fmt::Display for EventMeshMessage {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "EventMeshMessage {{")?;
        if let Some(biz_seq_no) = &self.biz_seq_no {
            write!(f, " biz_seq_no: {},", biz_seq_no)?;
        }
        if let Some(unique_id) = &self.unique_id {
            write!(f, " unique_id: {},", unique_id)?;
        }
        if let Some(topic) = &self.topic {
            write!(f, " topic: {},", topic)?;
        }
        if let Some(content) = &self.content {
            write!(f, " content: {},", content)?;
        }
        write!(f, " prop: {{")?;
        for (key, value) in &self.prop {
            write!(f, " {}: {},", key, value)?;
        }
        write!(f, " }},")?;
        write!(f, " create_time: {},", self.create_time)?;
        write!(f, " }}")
    }
}
impl Default for EventMeshMessage {
    fn default() -> Self {
        Self {
            biz_seq_no: None,
            unique_id: None,
            topic: None,
            content: None,
            prop: HashMap::with_capacity(0),
            create_time: SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .map_or_else(|_err| 0u64, |time| time.as_millis() as u64),
        }
    }
}

#[allow(dead_code)]
impl EventMeshMessage {
    pub fn new(
        biz_seq_no: impl Into<String>,
        unique_id: impl Into<String>,
        topic: impl Into<String>,
        content: impl Into<String>,
        prop: HashMap<String, String>,
        create_time: u64,
    ) -> Self {
        Self {
            biz_seq_no: Some(biz_seq_no.into()),
            unique_id: Some(unique_id.into()),
            topic: Some(topic.into()),
            content: Some(content.into()),
            prop,
            create_time,
        }
    }

    pub fn add_prop(mut self, key: String, val: String) -> Self {
        self.prop.insert(key, val);
        self
    }

    pub fn get_prop(&self, key: &str) -> Option<&String> {
        self.prop.get(key)
    }

    pub fn remove_prop_if_present(mut self, key: &str) -> Self {
        self.prop.remove(key);
        self
    }

    pub fn with_biz_seq_no(mut self, biz_seq_no: impl Into<String>) -> Self {
        self.biz_seq_no = Some(biz_seq_no.into());
        self
    }

    pub fn with_unique_id(mut self, unique_id: impl Into<String>) -> Self {
        self.unique_id = Some(unique_id.into());
        self
    }

    pub fn with_topic(mut self, topic: impl Into<String>) -> Self {
        self.topic = Some(topic.into());
        self
    }

    pub fn with_content(mut self, content: impl Into<String>) -> Self {
        self.content = Some(content.into());
        self
    }

    pub fn with_create_time(mut self, create_time: u64) -> Self {
        self.create_time = create_time;
        self
    }
}

impl FromPbCloudEvent<EventMeshMessage> for EventMeshMessage {
    fn from_pb_cloud_event(event: &PbCloudEvent) -> Option<EventMeshMessage> {
        Some(EventMeshCloudEventUtils::switch_event_mesh_cloud_event_2_event_mesh_message(event))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_default() {
        let default_msg = EventMeshMessage::default();

        assert_eq!(default_msg.biz_seq_no, None);
        assert_eq!(default_msg.unique_id, None);
        assert_eq!(default_msg.topic, None);
        assert_eq!(default_msg.content, None);
        assert!(default_msg.prop.is_empty());
    }

    #[test]
    fn test_new() {
        let msg = EventMeshMessage::new(
            "biz_seq_123",
            "unique_456",
            "test_topic",
            "message_content",
            HashMap::new(),
            1234567890,
        );

        assert_eq!(msg.biz_seq_no, Some("biz_seq_123".to_string()));
        assert_eq!(msg.unique_id, Some("unique_456".to_string()));
        assert_eq!(msg.topic, Some("test_topic".to_string()));
        assert_eq!(msg.content, Some("message_content".to_string()));
        assert!(msg.prop.is_empty());
        assert_eq!(msg.create_time, 1234567890);
    }

    #[test]
    fn test_add_prop() {
        let mut msg = EventMeshMessage::default();

        msg = msg.add_prop("key1".to_string(), "value1".to_string());
        msg = msg.add_prop("key2".to_string(), "value2".to_string());

        assert_eq!(msg.get_prop("key1"), Some(&"value1".to_string()));
        assert_eq!(msg.get_prop("key2"), Some(&"value2".to_string()));
    }
}
