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

//! Internal webhook handler used by the built-in [`WebhookServer`].
//!
//! This module is **not** part of the public API. It wires the push-body codec
//! ([`crate::transport::http::codec::parse_push_body`]) together with a
//! [`MessageListener`] into an axum handler consumed exclusively by
//! [`WebhookServer`](crate::transport::http::server::WebhookServer).
//!
//! Users who want to host their own HTTP endpoint (with axum, actix, plain
//! hyper, or any other framework) should ignore this module and build on the
//! public codec utilities directly — see the `consumer_custom` example and the
//! [`codec`](crate::transport::http::codec) module docs.

use std::sync::Arc;

use axum::extract::State;
use axum::http::HeaderMap;
use axum::response::IntoResponse;
use axum::Json;
use bytes::Bytes;
use tracing::{debug, error, warn};

use crate::error::EventMeshError;
use crate::message::Message;
use crate::model::{EventMeshMessage, EventMeshProtocolType};
use crate::transport::http::codec::{parse_push_body, WebhookReply};
use crate::MessageListener;

/// Message representation supported by the built-in webhook decoder.
pub trait WebhookMessage: Send + 'static {
    fn decode_webhook(
        body: &crate::transport::http::codec::PushMessageRequestBody,
        headers: &HeaderMap,
    ) -> crate::Result<Self>
    where
        Self: Sized;
}

impl WebhookMessage for EventMeshMessage {
    fn decode_webhook(
        body: &crate::transport::http::codec::PushMessageRequestBody,
        _headers: &HeaderMap,
    ) -> crate::Result<Self> {
        body.to_event_mesh_message()
    }
}

impl WebhookMessage for Message {
    fn decode_webhook(
        body: &crate::transport::http::codec::PushMessageRequestBody,
        headers: &HeaderMap,
    ) -> crate::Result<Self> {
        let header_protocol_type = headers
            .get("protocoltype")
            .map(|value| {
                value.to_str().map_err(|error| EventMeshError::Protocol {
                    transport: "http",
                    message: format!("invalid protocoltype header: {error}"),
                })
            })
            .transpose()?;
        let extension_protocol_type =
            body.extfields
                .as_deref()
                .filter(|fields| !fields.trim().is_empty())
                .map(|fields| {
                    serde_json::from_str::<std::collections::HashMap<String, String>>(fields)
                        .map_err(|error| EventMeshError::Protocol {
                            transport: "http",
                            message: format!("failed to parse extFields JSON: {error}"),
                        })
                })
                .transpose()?
                .and_then(|fields| fields.get("protocoltype").cloned());

        if let (Some(header), Some(extension)) =
            (header_protocol_type, extension_protocol_type.as_deref())
        {
            if header != extension {
                return Err(EventMeshError::Protocol {
                    transport: "http",
                    message: format!(
                        "conflicting protocoltype values: header={header:?}, \
                         extFields={extension:?}"
                    ),
                });
            }
        }

        // Runtime HTTP pushes created from an HttpCommand do not carry the
        // original `protocoltype` as an HTTP header. They do retain all
        // CloudEvent extensions in the form-level `extFields`, including
        // `protocoltype`, so consult that field before applying the legacy
        // native-message default.
        let protocol_type = header_protocol_type
            .or(extension_protocol_type.as_deref())
            .unwrap_or(EventMeshProtocolType::EventMeshMessage.as_str());

        if protocol_type == EventMeshProtocolType::CloudEvents.as_str() {
            #[cfg(feature = "cloud_events")]
            {
                return serde_json::from_str(&body.content)
                    .map(Self::CloudEvent)
                    .map_err(crate::error::EventMeshError::Codec);
            }

            #[cfg(not(feature = "cloud_events"))]
            return Err(EventMeshError::Unsupported(
                "received a CloudEvent without the 'cloud_events' feature enabled".into(),
            ));
        }

        if protocol_type != EventMeshProtocolType::EventMeshMessage.as_str() {
            return Err(EventMeshError::Protocol {
                transport: "http",
                message: format!("unsupported protocoltype {protocol_type:?}"),
            });
        }

        Ok(Self::EventMesh(body.to_event_mesh_message()?))
    }
}

/// Shared state for the webhook handler, holding the message listener.
pub(crate) struct WebhookState<L: MessageListener>
where
    L::Message: WebhookMessage,
{
    listener: Arc<L>,
}

impl<L: MessageListener> WebhookState<L>
where
    L::Message: WebhookMessage,
{
    /// Create state wrapping the given listener.
    pub(crate) fn new(listener: Arc<L>) -> Self {
        Self { listener }
    }
}

impl<L: MessageListener> Clone for WebhookState<L>
where
    L::Message: WebhookMessage,
{
    fn clone(&self) -> Self {
        Self {
            listener: Arc::clone(&self.listener),
        }
    }
}

/// Internal axum handler used by [`WebhookServer`](crate::transport::http::server::WebhookServer).
///
/// Not part of the public API. To receive pushes on your own server, implement
/// a handler with the public [`codec`](crate::transport::http::codec) helpers
/// instead (see the `consumer_custom` example).
pub(crate) struct WebhookHandler;

impl WebhookHandler {
    /// The actual handler function. Extracts the body bytes, parses the
    /// form-urlencoded push body, dispatches to the listener, and returns the
    /// JSON acknowledgment `{"retCode": <int>}`.
    pub(crate) async fn handle<L: MessageListener>(
        State(state): State<WebhookState<L>>,
        headers: HeaderMap,
        body: Bytes,
    ) -> impl IntoResponse
    where
        L::Message: WebhookMessage,
    {
        let body_str = match std::str::from_utf8(&body) {
            Ok(s) => s,
            Err(e) => {
                warn!("webhook body not UTF-8: {e}");
                return Json(WebhookReply::retry("invalid UTF-8")).into_response();
            }
        };

        let push_body = match parse_push_body(body_str) {
            Ok(b) => b,
            Err(e) => {
                warn!("webhook body parse error: {e}");
                return Json(WebhookReply::retry("form decode error")).into_response();
            }
        };

        let msg = match L::Message::decode_webhook(&push_body, &headers) {
            Ok(m) => m,
            Err(e) => {
                error!("webhook message decode error: {e}");
                return Json(WebhookReply::retry("message decode error")).into_response();
            }
        };

        debug!("webhook received a message");

        match state.listener.handle(msg).await {
            Ok(Some(reply)) => {
                // The listener produced a reply, but the HTTP webhook transport
                // cannot deliver it: the runtime's protocol adaptor does not
                // support REPLY_MESSAGE (code 301) on the CloudEvents path, so
                // there is no wire path to route the reply back to the original
                // requester. SYNC subscriptions are rejected at subscribe time;
                // this warning is a defensive backstop for messages pushed from
                // a non-Rust consumer or a legacy subscription.
                warn!(
                    "listener produced a reply (type={}) but the HTTP webhook \
                     transport cannot deliver replies; use the gRPC transport for \
                     request/reply",
                    std::any::type_name_of_val(&reply)
                );
                Json(WebhookReply::ok()).into_response()
            }
            Ok(None) => Json(WebhookReply::ok()).into_response(),
            Err(error) => {
                warn!(%error, "webhook handler failed; requesting redelivery");
                Json(WebhookReply::retry("handler failed")).into_response()
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn push(content: String) -> crate::transport::http::codec::PushMessageRequestBody {
        crate::transport::http::codec::PushMessageRequestBody {
            content,
            bizseqno: Some("seq-1".into()),
            unique_id: Some("id-1".into()),
            random_no: None,
            topic: Some("orders".into()),
            extfields: None,
        }
    }

    fn push_with_protocol(
        content: String,
        protocol_type: &str,
    ) -> crate::transport::http::codec::PushMessageRequestBody {
        let mut body = push(content);
        body.extfields = Some(
            serde_json::json!({
                "protocoltype": protocol_type,
                "protocoldesc": "http"
            })
            .to_string(),
        );
        body
    }

    #[test]
    fn public_message_rejects_unknown_protocol() {
        let mut headers = HeaderMap::new();
        headers.insert("protocoltype", "openmessage".parse().unwrap());
        let error = Message::decode_webhook(&push("created".into()), &headers).unwrap_err();
        assert!(matches!(
            error,
            EventMeshError::Protocol {
                transport: "http",
                ..
            }
        ));
    }

    #[cfg(feature = "cloud_events")]
    #[test]
    fn public_message_preserves_cloud_event_protocol() {
        use cloudevents::{EventBuilder, EventBuilderV10};

        let event = EventBuilderV10::new()
            .id("event-1")
            .source("urn:test")
            .ty("orders.created")
            .build()
            .unwrap();
        let mut headers = HeaderMap::new();
        headers.insert("protocoltype", "cloudevents".parse().unwrap());
        let decoded =
            Message::decode_webhook(&push(serde_json::to_string(&event).unwrap()), &headers)
                .unwrap();
        assert_eq!(decoded, Message::CloudEvent(event));
    }

    #[cfg(feature = "cloud_events")]
    #[test]
    fn public_message_recovers_cloud_event_protocol_from_extfields() {
        use cloudevents::{EventBuilder, EventBuilderV10};

        let event = EventBuilderV10::new()
            .id("event-1")
            .source("urn:test")
            .ty("orders.created")
            .build()
            .unwrap();
        let decoded = Message::decode_webhook(
            &push_with_protocol(serde_json::to_string(&event).unwrap(), "cloudevents"),
            &HeaderMap::new(),
        )
        .unwrap();
        assert_eq!(decoded, Message::CloudEvent(event));
    }

    #[test]
    fn public_message_rejects_conflicting_protocol_sources() {
        let mut headers = HeaderMap::new();
        headers.insert("protocoltype", "eventmeshmessage".parse().unwrap());
        let error = Message::decode_webhook(
            &push_with_protocol("created".into(), "cloudevents"),
            &headers,
        )
        .unwrap_err();
        assert!(matches!(
            error,
            EventMeshError::Protocol {
                transport: "http",
                ..
            }
        ));
    }
}
