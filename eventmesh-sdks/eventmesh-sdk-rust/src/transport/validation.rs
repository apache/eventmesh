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

//! Validation for new native publishes, broadcasts, and outgoing requests.
//!
//! Producer operations apply these policies before encoding. Shared encoders
//! also serve handler replies and must not impose producer-only restrictions.

use crate::{Error, EventMeshMessage, Result};

pub(crate) fn validate_native_publish(message: &EventMeshMessage) -> Result<()> {
    if message.topic().trim().is_empty() {
        return Err(Error::InvalidMessage("topic is required".into()));
    }
    if let Some(ttl) = message.ttl_millis() {
        if !(1..=i64::from(i32::MAX)).contains(&ttl) {
            return Err(Error::InvalidMessage(format!(
                "ttl must be between 1 and {} milliseconds; EventMesh does not define a never-expire value",
                i32::MAX,
            )));
        }
    }
    Ok(())
}

#[cfg(feature = "grpc")]
pub(crate) fn validate_grpc_publish(message: &EventMeshMessage) -> Result<()> {
    validate_native_publish(message)?;
    if message.content().is_empty() {
        return Err(Error::InvalidMessage("content is required".into()));
    }
    Ok(())
}

#[cfg(feature = "tcp")]
pub(crate) fn validate_tcp_publish(message: &EventMeshMessage) -> Result<()> {
    validate_native_publish(message)?;
    if message.content().trim().is_empty() {
        return Err(Error::InvalidMessage("content is required".into()));
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[cfg(all(feature = "grpc", feature = "tcp"))]
    #[test]
    fn publish_validation_is_transport_specific() {
        let empty = EventMeshMessage::new("topic", "").unwrap();
        assert!(validate_native_publish(&empty).is_ok());
        assert!(validate_grpc_publish(&empty).is_err());
        assert!(validate_tcp_publish(&empty).is_err());

        let whitespace = EventMeshMessage::new("topic", "\t").unwrap();
        assert!(validate_grpc_publish(&whitespace).is_ok());
        assert!(validate_tcp_publish(&whitespace).is_err());
    }

    #[test]
    fn ttl_is_preserved_at_construction_and_validated_for_publish() {
        let message = EventMeshMessage::builder()
            .topic("topic")
            .content("content")
            .ttl_millis(0)
            .build()
            .unwrap();
        assert_eq!(message.ttl_millis(), Some(0));
        assert!(validate_native_publish(&message).is_err());
    }

    #[test]
    fn publish_validation_uses_only_typed_ttl() {
        EventMeshMessage::builder()
            .topic("topic")
            .content("content")
            .ttl_millis(4_000)
            .build()
            .unwrap();

        let invalid = EventMeshMessage::builder()
            .topic("topic")
            .content("content")
            .ttl_millis(-1)
            .build()
            .unwrap();
        assert!(validate_native_publish(&invalid).is_err());
    }
}
