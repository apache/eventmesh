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
//!
//!

/// Re-export eventmesh main.
pub use eventmesh::main;

/// Re-export eventmesh as tokio.
pub use tokio as eventmesh;

/// Shorthand for `anyhow::Result`.
pub type Result<T> = anyhow::Result<T>;

// Modules

/// Configurations.
pub mod config;

/// Errors.
pub(crate) mod error;

/// Network utils.
mod net;

/// Common utilities.  
pub mod common;

/// gRPC client implementations.
pub mod grpc;

/// Logging.
pub mod log;

/// Data models.
pub mod model;

/// Module contains Protobuf CloudEvent related types and builder.
pub mod proto_cloud_event {
    use crate::common::ProtocolKey;

    /// Protobuf CloudEvent attribute value enum.    
    pub use crate::grpc::pb::cloud_events::cloud_event::cloud_event_attribute_value::Attr as PbAttr;
    use crate::grpc::pb::cloud_events::cloud_event::cloud_event_attribute_value::Attr;
    pub use crate::grpc::pb::cloud_events::cloud_event::CloudEventAttributeValue as PbCloudEventAttributeValue;
    pub use crate::grpc::pb::cloud_events::cloud_event::Data as PbData;
    use crate::grpc::pb::cloud_events::cloud_event::{CloudEventAttributeValue, Data};

    /// Protobuf CloudEvent message.
    pub use crate::grpc::pb::cloud_events::{
        CloudEvent as PbCloudEvent, CloudEventBatch as PbCloudEventBatch,
    };

    impl ToString for PbAttr {
        /// Convert Protobuf attribute to String.
        fn to_string(&self) -> String {
            match self {
                Attr::CeBoolean(value) => value.to_string(),
                Attr::CeInteger(value) => value.to_string(),
                Attr::CeString(value) => value.clone(),
                Attr::CeBytes(value) => unsafe { String::from_utf8_unchecked(value.clone()) },
                Attr::CeUri(value) => value.clone(),
                Attr::CeUriRef(value) => value.clone(),
                Attr::CeTimestamp(value) => value.to_string(),
            }
        }
    }

    impl ToString for PbData {
        /// Convert Protobuf data to String.
        fn to_string(&self) -> String {
            match self {
                Data::BinaryData(value) => unsafe { String::from_utf8_unchecked(value.clone()) },
                Data::TextData(value) => value.clone(),
                Data::ProtoData(value) => unsafe {
                    String::from_utf8_unchecked(value.value.clone())
                },
            }
        }
    }

    /// Builder for constructing Protobuf CloudEvent.
    #[derive(Debug, Default)]
    pub struct EventMeshCloudEventBuilder {
        /// Environment attribute.
        pub(crate) env: String,

        /// IDC attribute.  
        pub(crate) idc: String,

        /// IP address attribute.
        pub(crate) ip: String,

        /// Optional process ID attribute.
        pub(crate) pid: Option<String>,

        /// System attribute.
        pub(crate) sys: String,

        /// Username attribute.
        pub(crate) user_name: String,

        /// Password attribute.
        pub(crate) password: String,

        /// Language attribute.
        pub(crate) language: String,

        /// Protocol type attribute.
        pub(crate) protocol_type: String,

        /// Protocol version attribute.
        pub(crate) protocol_version: String,

        /// TTL attribute.
        pub(crate) ttl: String,

        /// Subject attribute.
        pub(crate) subject: String,

        /// Producer group attribute.
        pub(crate) producergroup: String,

        /// Unique ID attribute.
        pub(crate) uniqueid: String,

        /// Data content type attribute.
        pub(crate) data_content_type: String,
    }

    impl EventMeshCloudEventBuilder {
        /// Set process ID attribute.
        pub fn with_pid(mut self, pid: impl Into<String>) -> Self {
            self.pid = Some(pid.into());
            self
        }

        /// Set environment attribute.
        pub fn with_env(mut self, env: impl Into<String>) -> Self {
            self.env = env.into();
            self
        }

        /// Set IDC attribute.
        pub fn with_idc(mut self, idc: impl Into<String>) -> Self {
            self.idc = idc.into();
            self
        }

        /// Set IP address attribute.
        pub fn with_ip(mut self, ip: impl Into<String>) -> Self {
            self.ip = ip.into();
            self
        }

        /// Set system attribute.
        pub fn with_sys(mut self, sys: impl Into<String>) -> Self {
            self.sys = sys.into();
            self
        }

        /// Set username attribute.
        pub fn with_user_name(mut self, user_name: impl Into<String>) -> Self {
            self.user_name = user_name.into();
            self
        }

        /// Set password attribute.
        pub fn with_password(mut self, password: impl Into<String>) -> Self {
            self.password = password.into();
            self
        }

        /// Set language attribute.
        pub fn with_language(mut self, language: impl Into<String>) -> Self {
            self.language = language.into();
            self
        }

        /// Set protocol type attribute.
        pub fn with_protocol_type(mut self, protocol_type: impl Into<String>) -> Self {
            self.protocol_type = protocol_type.into();
            self
        }

        /// Set protocol version attribute.
        pub fn with_protocol_version(mut self, protocol_version: impl Into<String>) -> Self {
            self.protocol_version = protocol_version.into();
            self
        }

        /// Set TTL attribute.
        pub fn with_ttl(mut self, ttl: impl Into<String>) -> Self {
            self.ttl = ttl.into();
            self
        }

        /// Set subject attribute.
        pub fn with_subject(mut self, subject: impl Into<String>) -> Self {
            self.subject = subject.into();
            self
        }

        /// Set producer group attribute.
        pub fn with_producergroup(mut self, producergroup: impl Into<String>) -> Self {
            self.producergroup = producergroup.into();
            self
        }

        /// Set unique ID attribute.
        pub fn with_uniqueid(mut self, uniqueid: impl Into<String>) -> Self {
            self.uniqueid = uniqueid.into();
            self
        }

        /// Set data content type attribute.
        pub fn with_data_content_type(mut self, data_content_type: impl Into<String>) -> Self {
            self.data_content_type = data_content_type.into();
            self
        }

        /// Build the Protobuf CloudEvent
        pub fn build(&self) -> PbCloudEvent {
            let mut cloud_event = PbCloudEvent::default();
            cloud_event.attributes.insert(
                ProtocolKey::ENV.to_string(),
                Self::build_cloud_event_attr(&self.env),
            );
            cloud_event.attributes.insert(
                ProtocolKey::IDC.to_string(),
                Self::build_cloud_event_attr(&self.idc),
            );
            cloud_event.attributes.insert(
                ProtocolKey::IP.to_string(),
                Self::build_cloud_event_attr(&self.ip),
            );
            if let Some(ref pid) = self.pid {
                cloud_event.attributes.insert(
                    ProtocolKey::PID.to_string(),
                    Self::build_cloud_event_attr(pid),
                );
            }
            cloud_event.attributes.insert(
                ProtocolKey::SYS.to_string(),
                Self::build_cloud_event_attr(&self.sys),
            );
            cloud_event.attributes.insert(
                ProtocolKey::LANGUAGE.to_string(),
                Self::build_cloud_event_attr(&self.language),
            );
            cloud_event.attributes.insert(
                ProtocolKey::USERNAME.to_string(),
                Self::build_cloud_event_attr(&self.user_name),
            );
            cloud_event.attributes.insert(
                ProtocolKey::PASSWD.to_string(),
                Self::build_cloud_event_attr(&self.password),
            );
            cloud_event.attributes.insert(
                ProtocolKey::PROTOCOL_TYPE.to_string(),
                Self::build_cloud_event_attr(&self.protocol_type),
            );
            cloud_event.attributes.insert(
                ProtocolKey::PROTOCOL_VERSION.to_string(),
                Self::build_cloud_event_attr(&self.protocol_version),
            );
            cloud_event.attributes.insert(
                ProtocolKey::TTL.to_string(),
                Self::build_cloud_event_attr(&self.ttl),
            );
            cloud_event.attributes.insert(
                ProtocolKey::SUBJECT.to_string(),
                Self::build_cloud_event_attr(&self.subject),
            );
            cloud_event.attributes.insert(
                ProtocolKey::PRODUCERGROUP.to_string(),
                Self::build_cloud_event_attr(&self.producergroup),
            );
            cloud_event.attributes.insert(
                ProtocolKey::UNIQUE_ID.to_string(),
                Self::build_cloud_event_attr(&self.uniqueid),
            );
            cloud_event.attributes.insert(
                ProtocolKey::DATA_CONTENT_TYPE.to_string(),
                Self::build_cloud_event_attr(&self.data_content_type),
            );
            cloud_event
        }

        /// Helper method to build a Protobuf CloudEvent attribute value.
        fn build_cloud_event_attr(value: impl Into<String>) -> CloudEventAttributeValue {
            let mut attr_value = PbCloudEventAttributeValue::default();
            attr_value.attr = Some(PbAttr::CeString(value.into()));
            attr_value
        }
    }
}
