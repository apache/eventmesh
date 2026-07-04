//
// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with this
// work for additional information regarding copyright ownership.  The ASF
// licenses this file to You under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance with the
// License.  You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
// WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
// License for the specific language governing permissions and limitations
// under the License.
//

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

use crate::model::EventMeshMessage;
use crate::transport::http::codec::{parse_push_body, WebhookReply};
use crate::MessageListener;

/// Shared state for the webhook handler, holding the message listener.
pub(crate) struct WebhookState<L: MessageListener<Message = EventMeshMessage>> {
    listener: Arc<L>,
}

impl<L: MessageListener<Message = EventMeshMessage>> WebhookState<L> {
    /// Create state wrapping the given listener.
    pub(crate) fn new(listener: Arc<L>) -> Self {
        Self { listener }
    }
}

impl<L: MessageListener<Message = EventMeshMessage>> Clone for WebhookState<L> {
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
    pub(crate) async fn handle<L: MessageListener<Message = EventMeshMessage>>(
        State(state): State<WebhookState<L>>,
        _headers: HeaderMap,
        body: Bytes,
    ) -> impl IntoResponse {
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

        let msg = match push_body.to_event_mesh_message() {
            Ok(m) => m,
            Err(e) => {
                error!("webhook message decode error: {e}");
                return Json(WebhookReply::retry("message decode error")).into_response();
            }
        };

        debug!(
            "webhook received topic={:?} bizseqno={:?}",
            msg.topic, msg.biz_seq_no
        );

        match state.listener.handle(msg).await {
            Some(reply) => {
                // The listener produced a reply, but the HTTP webhook transport
                // cannot deliver it: the runtime's protocol adaptor does not
                // support REPLY_MESSAGE (code 301) on the CloudEvents path, so
                // there is no wire path to route the reply back to the original
                // requester. SYNC subscriptions are rejected at subscribe time;
                // this warning is a defensive backstop for messages pushed from
                // a non-Rust consumer or a legacy subscription.
                warn!(
                    "listener produced a reply (topic={:?}) but the HTTP webhook \
                     transport cannot deliver replies; use the gRPC transport for \
                     request/reply",
                    reply.topic
                );
                Json(WebhookReply::ok()).into_response()
            }
            None => Json(WebhookReply::ok()).into_response(),
        }
    }
}
