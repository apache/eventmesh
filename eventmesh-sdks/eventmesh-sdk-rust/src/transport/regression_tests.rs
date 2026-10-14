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

use crate::transport::{
    grpc::{codec, consumer::build_reply},
    tcp::{
        codec::TcpCodec,
        frame::{Command, Header, Package, PackageBody},
    },
};
use crate::{
    config::{ConsumerOptions, Credentials, Endpoint, GrpcConfig, ReconnectPolicy, TcpConfig},
    Message, MessageHandler, Result, Subscription, TcpClient,
};
use cloudevents::{EventBuilder, EventBuilderV10};
use futures::{SinkExt, StreamExt};
use std::{sync::Arc, time::Duration};
use tokio::{
    net::TcpListener,
    sync::{Notify, Semaphore},
};
use tokio_util::codec::Framed;

fn config() -> GrpcConfig {
    GrpcConfig::new(Endpoint::new("127.0.0.1", 10205).unwrap())
}

fn text_event(id: &str) -> cloudevents::Event {
    EventBuilderV10::new()
        .id(id)
        .source("urn:test")
        .ty("test")
        .subject("orders")
        .data("text/plain", "pong")
        .build()
        .unwrap()
}

#[test]
fn text_cloud_event_reply_preserves_decodable_content_type() {
    let event = text_event("reply");
    let request = codec::from_cloudevent(&event, &config(), "group").unwrap();
    let wire = build_reply(&Message::CloudEvent(event), &request, &config()).unwrap();
    let result = codec::to_cloudevent(wire);
    assert!(
        result.is_ok(),
        "text/plain reply became undecodable: {result:?}"
    );
}

#[test]
fn grpc_reply_keeps_json_and_binary_payloads_and_business_attributes() {
    use cloudevents::{AttributesReader, Data};
    for (content_type, data) in [
        (
            "application/json",
            Data::Json(serde_json::json!({"reply": true})),
        ),
        ("application/octet-stream", Data::Binary(vec![0, 128, 255])),
    ] {
        let mut reply = EventBuilderV10::new()
            .id("reply-id")
            .source("urn:reply")
            .ty("reply-type")
            .subject("orders")
            .data(content_type, data.clone())
            .build()
            .unwrap();
        reply.set_extension("tenant", "reply-tenant");
        let mut request =
            codec::from_cloudevent(&text_event("request"), &config(), "group").unwrap();
        request.attributes.insert(
            "tenant".into(),
            crate::proto_gen::attr_str("request-tenant"),
        );
        request.attributes.insert(
            "requestid".into(),
            crate::proto_gen::attr_str("business-id"),
        );
        let wire = build_reply(&Message::CloudEvent(reply), &request, &config()).unwrap();
        let decoded = codec::to_cloudevent(wire).unwrap();
        assert_eq!(decoded.id(), "reply-id");
        assert_eq!(decoded.source().to_string(), "urn:reply");
        assert_eq!(decoded.ty(), "reply-type");
        assert_eq!(decoded.datacontenttype(), Some(content_type));
        assert_eq!(decoded.data(), Some(&data));
        assert_eq!(
            decoded.extension("tenant").unwrap().to_string(),
            "reply-tenant"
        );
        assert_eq!(
            decoded.extension("requestid").unwrap().to_string(),
            "business-id"
        );
    }
}

#[test]
fn tcp_cloud_event_reply_overrides_only_current_delivery_route() {
    use crate::transport::tcp::{delivery::inherit_request_metadata, message};
    use cloudevents::AttributesReader;
    let mut current = text_event("request");
    current.set_extension("correlation99id", "current-request");
    current.set_extension("reply99to99client", "current-client");
    current.set_extension("tenant", "request-tenant");
    let reply = EventBuilderV10::new()
        .id("reply")
        .source("urn:reply")
        .ty("reply-type")
        .subject("orders")
        .data(
            "application/cloudevents+json",
            serde_json::json!({"ok":true}),
        )
        .extension("correlation99id", "foreign-request")
        .extension("reply99to99client", "foreign-client")
        .extension("username", "foreign-user")
        .extension("tenant", "reply-tenant")
        .build()
        .unwrap();
    let mut reply = Message::CloudEvent(reply);
    inherit_request_metadata(&mut reply, &Message::CloudEvent(current));
    let Message::CloudEvent(event) = reply else {
        unreachable!()
    };
    let wire = message::build_cloud_event_package(&event, Command::ResponseToServer).unwrap();
    let decoded = message::parse_cloud_event(&wire.body).unwrap();
    assert_eq!(decoded.id(), "reply");
    assert_eq!(
        decoded.extension("tenant").unwrap().to_string(),
        "reply-tenant"
    );
    assert_eq!(
        decoded.extension("correlation99id").unwrap().to_string(),
        "current-request"
    );
    assert_eq!(
        decoded.extension("reply99to99client").unwrap().to_string(),
        "current-client"
    );
    assert!(decoded.extension("username").is_none());
}

#[test]
fn new_cloud_event_operation_filters_reserved_metadata_without_mutating_input() {
    use crate::transport::{metadata::outbound_cloud_event, tcp::message};
    use cloudevents::AttributesReader;
    let event = EventBuilderV10::new()
        .id("original")
        .source("urn:test")
        .ty("business-type")
        .subject("orders")
        .data(
            "application/cloudevents+json",
            serde_json::json!({"ok":true}),
        )
        .extension("token", "old-secret")
        .extension("username", "old-user")
        .extension("sys", "old-client")
        .extension("correlation99id", "old-route")
        .extension("tenant", "store-a")
        .extension("requestid", "business-request")
        .build()
        .unwrap();
    let grpc =
        codec::to_cloudevent(codec::from_cloudevent(&event, &config(), "group").unwrap()).unwrap();
    let tcp = message::parse_cloud_event(
        &message::build_cloud_event_package(&event, Command::RequestToServer)
            .unwrap()
            .body,
    )
    .unwrap();
    let http = outbound_cloud_event(&event, false);
    for outbound in [grpc, tcp, http] {
        assert_eq!(outbound.id(), event.id());
        assert_eq!(outbound.data(), event.data());
        assert_eq!(outbound.extension("tenant").unwrap().to_string(), "store-a");
        assert_eq!(
            outbound.extension("requestid").unwrap().to_string(),
            "business-request"
        );
        assert!(outbound.extension("token").is_none());
        assert!(outbound.extension("correlation99id").is_none());
        assert_ne!(
            outbound
                .extension("username")
                .map(ToString::to_string)
                .as_deref(),
            Some("old-user")
        );
        assert_ne!(
            outbound
                .extension("sys")
                .map(ToString::to_string)
                .as_deref(),
            Some("old-client")
        );
    }
    assert_eq!(event.extension("token").unwrap().to_string(), "old-secret");
    assert_eq!(
        event.extension("correlation99id").unwrap().to_string(),
        "old-route"
    );
}

#[test]
fn forwarded_cloud_event_drops_previous_sender_token() {
    let source = config().with_credentials(Credentials::new().with_token("source-token"));
    let wire = codec::from_cloudevent(&text_event("source"), &source, "source").unwrap();
    let received = codec::to_cloudevent(wire).unwrap();
    let forwarded = codec::from_cloudevent(&received, &config(), "destination").unwrap();
    assert!(
        !forwarded.attributes.contains_key("token"),
        "source token leaked into destination request"
    );
}

#[test]
fn cloud_event_reply_uses_current_request_routing() {
    let mut request = codec::from_cloudevent(&text_event("request"), &config(), "group").unwrap();
    request.attributes.insert(
        "correlation99id".into(),
        crate::proto_gen::attr_str("current-request"),
    );
    let mut reply = text_event("foreign-reply");
    reply.set_extension("correlation99id", "foreign-request");
    let result = build_reply(&Message::CloudEvent(reply), &request, &config()).unwrap();
    assert_eq!(
        crate::proto_gen::attr_as_str(&result.attributes["correlation99id"]),
        "current-request"
    );
}

struct SlowHandler {
    entered: Arc<Notify>,
    release: Arc<Semaphore>,
}

impl MessageHandler for SlowHandler {
    async fn handle(&self, _: Message) -> Result<Option<Message>> {
        self.entered.notify_one();
        self.release.acquire().await.unwrap().forget();
        Ok(Some(
            crate::EventMeshMessage::new("orders", "reply")?.into(),
        ))
    }
}

fn response(command: Command, request: &Package) -> Package {
    Package::new(Header::new(
        command,
        request.header.seq.clone().unwrap_or_default(),
    ))
}

#[tokio::test]
async fn tcp_inflight_delivery_does_not_ack_on_new_session() {
    late_delivery_keeps_its_session(Command::AsyncMessageToClient).await;
}

#[tokio::test]
async fn tcp_inflight_request_does_not_reply_on_new_session() {
    late_delivery_keeps_its_session(Command::RequestToClient).await;
}

async fn late_delivery_keeps_its_session(command: Command) {
    let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let port = listener.local_addr().unwrap().port();
    let entered = Arc::new(Notify::new());
    let release = Arc::new(Semaphore::new(0));
    let handler = SlowHandler {
        entered: entered.clone(),
        release: release.clone(),
    };
    let (replayed_tx, replayed_rx) = tokio::sync::oneshot::channel();
    let server = tokio::spawn(async move {
        let (stream, _) = listener.accept().await.unwrap();
        let mut socket = Framed::new(stream, TcpCodec::new());
        for (expected, reply) in [
            (Command::HelloRequest, Command::HelloResponse),
            (Command::ListenRequest, Command::ListenResponse),
            (Command::SubscribeRequest, Command::SubscribeResponse),
        ] {
            let request = socket.next().await.unwrap().unwrap();
            assert_eq!(request.header.cmd, expected);
            socket.send(response(reply, &request)).await.unwrap();
        }
        socket
            .send(Package {
                header: Header::new(command, "old-session-delivery"),
                body: PackageBody::Text(r#"{"topic":"orders","body":"payload"}"#.into()),
            })
            .await
            .unwrap();
        entered.notified().await;
        drop(socket);
        let (stream, _) = listener.accept().await.unwrap();
        let mut socket = Framed::new(stream, TcpCodec::new());
        let hello = socket.next().await.unwrap().unwrap();
        assert_eq!(hello.header.cmd, Command::HelloRequest);
        socket
            .send(response(Command::HelloResponse, &hello))
            .await
            .unwrap();
        // Heartbeat proves the supervisor committed the new active generation.
        let heartbeat = socket.next().await.unwrap().unwrap();
        assert_eq!(heartbeat.header.cmd, Command::HeartbeatRequest);
        socket
            .send(response(Command::HeartbeatResponse, &heartbeat))
            .await
            .unwrap();
        release.add_permits(1);
        for (expected, reply) in [
            (Command::SubscribeRequest, Command::SubscribeResponse),
            (Command::ListenRequest, Command::ListenResponse),
        ] {
            loop {
                let frame = socket.next().await.unwrap().unwrap();
                if frame.header.cmd == Command::HeartbeatRequest {
                    socket
                        .send(response(Command::HeartbeatResponse, &frame))
                        .await
                        .unwrap();
                    continue;
                }
                assert_eq!(frame.header.cmd, expected, "old delivery crossed sessions");
                socket.send(response(reply, &frame)).await.unwrap();
                break;
            }
        }
        replayed_tx.send(()).unwrap();
        while let Some(frame) = socket.next().await {
            let frame = frame.unwrap();
            match frame.header.cmd {
                Command::HeartbeatRequest => socket
                    .send(response(Command::HeartbeatResponse, &frame))
                    .await
                    .unwrap(),
                Command::ClientGoodbyeRequest => break,
                command => panic!("old delivery was emitted after replay: {command:?}"),
            }
        }
    });
    let config = TcpConfig::new(Endpoint::new("127.0.0.1", port).unwrap())
        .with_control_timeout(Duration::from_secs(1))
        .with_heartbeat_interval(Duration::from_millis(20))
        .with_reconnect(
            ReconnectPolicy::default()
                .with_max_retries(1)
                .with_initial_backoff(Duration::from_millis(10)),
        );
    let client = TcpClient::new(config).unwrap();
    let consumer = client
        .consumer(ConsumerOptions::new("group"), handler)
        .await
        .unwrap();
    consumer
        .subscribe(Subscription::new("orders"))
        .await
        .unwrap();
    tokio::time::timeout(Duration::from_secs(3), replayed_rx)
        .await
        .unwrap()
        .unwrap();
    consumer.shutdown();
    consumer.join().await.unwrap();
    server.await.unwrap();
}

#[tokio::test]
async fn tcp_unsubscribe_cannot_be_undone_by_concurrent_replay() {
    subscription_replay_control(false).await;
}

#[tokio::test]
async fn tcp_shutdown_interrupts_blocked_subscription_replay() {
    subscription_replay_control(true).await;
}

async fn subscription_replay_control(shutdown_during_replay: bool) {
    let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let port = listener.local_addr().unwrap().port();
    let disconnect = Arc::new(Notify::new());
    let release = Arc::new(Semaphore::new(0));
    let (replaying_tx, replaying_rx) = tokio::sync::oneshot::channel();
    let (serialized_tx, serialized_rx) = tokio::sync::oneshot::channel();
    let server_disconnect = disconnect.clone();
    let server_release = release.clone();
    let server = tokio::spawn(async move {
        let (stream, _) = listener.accept().await.unwrap();
        let mut socket = Framed::new(stream, TcpCodec::new());
        for (expected, reply) in [
            (Command::HelloRequest, Command::HelloResponse),
            (Command::ListenRequest, Command::ListenResponse),
            (Command::SubscribeRequest, Command::SubscribeResponse),
        ] {
            let request = socket.next().await.unwrap().unwrap();
            assert_eq!(request.header.cmd, expected);
            socket.send(response(reply, &request)).await.unwrap();
        }
        server_disconnect.notified().await;
        drop(socket);
        let (stream, _) = listener.accept().await.unwrap();
        let mut socket = Framed::new(stream, TcpCodec::new());
        let hello = socket.next().await.unwrap().unwrap();
        socket
            .send(response(Command::HelloResponse, &hello))
            .await
            .unwrap();
        let replay = socket.next().await.unwrap().unwrap();
        assert_eq!(replay.header.cmd, Command::SubscribeRequest);
        replaying_tx.send(()).unwrap();
        if !shutdown_during_replay {
            assert!(
                tokio::time::timeout(Duration::from_millis(25), socket.next())
                    .await
                    .is_err(),
                "unsubscribe must wait until replay finishes"
            );
            serialized_tx.send(()).unwrap();
            server_release.acquire().await.unwrap().forget();
            socket
                .send(response(Command::SubscribeResponse, &replay))
                .await
                .unwrap();
            for (expected, reply) in [
                (Command::ListenRequest, Command::ListenResponse),
                (Command::UnsubscribeRequest, Command::UnsubscribeResponse),
            ] {
                let request = socket.next().await.unwrap().unwrap();
                assert_eq!(request.header.cmd, expected);
                socket.send(response(reply, &request)).await.unwrap();
            }
        }
        while let Some(frame) = socket.next().await {
            assert_eq!(frame.unwrap().header.cmd, Command::ClientGoodbyeRequest);
        }
    });
    struct Noop;
    impl MessageHandler for Noop {
        async fn handle(&self, _: Message) -> Result<Option<Message>> {
            Ok(None)
        }
    }
    let consumer = Arc::new(
        TcpClient::new(
            TcpConfig::new(Endpoint::new("127.0.0.1", port).unwrap())
                .with_control_timeout(Duration::from_secs(2))
                .with_reconnect(
                    ReconnectPolicy::default().with_initial_backoff(Duration::from_millis(10)),
                ),
        )
        .unwrap()
        .consumer(ConsumerOptions::new("group"), Noop)
        .await
        .unwrap(),
    );
    consumer
        .subscribe(Subscription::new("orders"))
        .await
        .unwrap();
    disconnect.notify_one();
    tokio::time::timeout(Duration::from_secs(3), replaying_rx)
        .await
        .unwrap()
        .unwrap();
    if !shutdown_during_replay {
        let role = consumer.clone();
        let unsubscribe = tokio::spawn(async move { role.unsubscribe_all().await });
        serialized_rx.await.unwrap();
        assert!(!unsubscribe.is_finished());
        release.add_permits(1);
        unsubscribe.await.unwrap().unwrap();
    }
    consumer.shutdown();
    tokio::time::timeout(Duration::from_millis(500), consumer.join())
        .await
        .expect("shutdown must interrupt replay, not wait for its control timeout")
        .unwrap();
    server.await.unwrap();
}

struct MockHttpRuntime {
    registered: std::sync::atomic::AtomicBool,
    heartbeat_entered: Notify,
    heartbeat_release: Semaphore,
    unsubscribed: Notify,
}

impl Default for MockHttpRuntime {
    fn default() -> Self {
        Self {
            registered: std::sync::atomic::AtomicBool::new(false),
            heartbeat_entered: Notify::new(),
            heartbeat_release: Semaphore::new(0),
            unsubscribed: Notify::new(),
        }
    }
}

async fn http_lifecycle_request(
    axum::extract::State(state): axum::extract::State<Arc<MockHttpRuntime>>,
    headers: http::HeaderMap,
) -> &'static str {
    use std::sync::atomic::Ordering;
    let code = headers["code"].to_str().unwrap().parse::<i32>().unwrap();
    if code == crate::transport::http::codec::subscribe_code() {
        state.registered.store(true, Ordering::SeqCst);
    } else if code == crate::transport::http::codec::unsubscribe_code() {
        state.registered.store(false, Ordering::SeqCst);
        state.unsubscribed.notify_one();
    } else if code == crate::transport::http::codec::heartbeat_code() {
        state.heartbeat_entered.notify_one();
        state.heartbeat_release.acquire().await.unwrap().forget();
        // Match Runtime HeartBeatProcessor: a heartbeat adds missing clients.
        state.registered.store(true, Ordering::SeqCst);
    }
    r#"{"retCode":0}"#
}

#[tokio::test]
async fn http_close_does_not_resurrect_registration_with_inflight_heartbeat() {
    use crate::{
        config::{EndpointSet, HttpConfig},
        HttpClient,
    };
    use std::sync::atomic::Ordering;
    let state = Arc::new(MockHttpRuntime::default());
    let app = axum::Router::new()
        .route("/", axum::routing::post(http_lifecycle_request))
        .route(
            "/eventmesh/heartbeat",
            axum::routing::post(http_lifecycle_request),
        )
        .with_state(state.clone());
    let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let port = listener.local_addr().unwrap().port();
    let server = tokio::spawn(async move { axum::serve(listener, app).await.unwrap() });
    let client = HttpClient::new(HttpConfig::new(
        EndpointSet::new([Endpoint::new("127.0.0.1", port).unwrap()]).unwrap(),
    ))
    .unwrap();
    let registration = Arc::new(
        client
            .webhook_registration(ConsumerOptions::new("group"))
            .unwrap(),
    );
    registration
        .subscribe(
            Subscription::new("orders"),
            "http://127.0.0.1:8080/callback",
        )
        .await
        .unwrap();
    tokio::time::pause();
    tokio::time::advance(Duration::from_secs(10)).await;
    tokio::time::resume();
    tokio::time::timeout(Duration::from_secs(2), state.heartbeat_entered.notified())
        .await
        .unwrap();
    let close = {
        let registration = registration.clone();
        tokio::spawn(async move { registration.close().await })
    };
    // Closing must wait for the submitted heartbeat before unregistering.
    assert!(
        tokio::time::timeout(Duration::from_millis(25), state.unsubscribed.notified())
            .await
            .is_err()
    );
    assert!(!close.is_finished());
    state.heartbeat_release.add_permits(1);
    close.await.unwrap().unwrap();
    assert!(matches!(
        registration
            .subscribe(
                Subscription::new("orders"),
                "http://127.0.0.1:8080/callback"
            )
            .await,
        Err(crate::Error::ChannelClosed(_))
    ));
    server.abort();
    assert!(
        !state.registered.load(Ordering::SeqCst),
        "close returned success, but its older heartbeat recreated the remote subscription"
    );
}

struct PanickingPublisherHandler;

impl MessageHandler for PanickingPublisherHandler {
    async fn handle(&self, _: Message) -> Result<Option<Message>> {
        panic!("publisher handler failed");
    }
}

#[tokio::test]
async fn publisher_handler_panic_closes_its_failed_session() {
    let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let port = listener.local_addr().unwrap().port();
    let server = tokio::spawn(async move {
        let (stream, _) = listener.accept().await.unwrap();
        let mut socket = Framed::new(stream, TcpCodec::new());
        let hello = socket.next().await.unwrap().unwrap();
        socket
            .send(response(Command::HelloResponse, &hello))
            .await
            .unwrap();
        socket
            .send(Package {
                header: Header::new(Command::ResponseToClient, "response-push"),
                body: PackageBody::Text(r#"{"topic":"orders","body":"payload"}"#.into()),
            })
            .await
            .unwrap();
        matches!(
            tokio::time::timeout(Duration::from_millis(150), socket.next()).await,
            Ok(None)
        )
    });
    let client = TcpClient::new(
        TcpConfig::new(Endpoint::new("127.0.0.1", port).unwrap())
            .with_reconnect(ReconnectPolicy::default().with_enabled(false)),
    )
    .unwrap();
    let producer = client
        .producer_with_handler(
            crate::config::ProducerOptions::new("group"),
            PanickingPublisherHandler,
        )
        .await
        .unwrap();
    let closed = server.await.unwrap();
    producer.shutdown().await;
    let error = producer.join().await.unwrap_err();
    assert!(error.to_string().contains("publisher handler failed"));
    assert!(closed, "panicked dispatch task left its connection and heartbeat running; no public wait reports the panic");
}

struct FailOncePublisherHandler(std::sync::atomic::AtomicBool);

impl MessageHandler for FailOncePublisherHandler {
    async fn handle(&self, _: Message) -> Result<Option<Message>> {
        if !self.0.swap(true, std::sync::atomic::Ordering::SeqCst) {
            panic!("first publisher delivery failed");
        }
        Ok(None)
    }
}

#[tokio::test]
async fn publisher_handler_recovers_and_join_can_be_cancelled() {
    let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let port = listener.local_addr().unwrap().port();
    let (recovered_tx, recovered_rx) = tokio::sync::oneshot::channel();
    let server = tokio::spawn(async move {
        for attempt in 0..2 {
            let (stream, _) = listener.accept().await.unwrap();
            let mut socket = Framed::new(stream, TcpCodec::new());
            let hello = socket.next().await.unwrap().unwrap();
            socket
                .send(response(Command::HelloResponse, &hello))
                .await
                .unwrap();
            socket
                .send(Package {
                    header: Header::new(Command::ResponseToClient, format!("push-{attempt}")),
                    body: PackageBody::Text(r#"{"topic":"orders","body":"payload"}"#.into()),
                })
                .await
                .unwrap();
            if attempt == 0 {
                assert!(
                    socket.next().await.is_none(),
                    "failed delivery must close without ACK"
                );
            } else {
                let ack = socket.next().await.unwrap().unwrap();
                assert_eq!(ack.header.cmd, Command::ResponseToClientAck);
                assert_eq!(ack.header.seq.as_deref(), Some("push-1"));
                recovered_tx.send(()).unwrap();
                while let Some(frame) = socket.next().await {
                    assert_eq!(frame.unwrap().header.cmd, Command::ClientGoodbyeRequest);
                }
                break;
            }
        }
    });
    let producer = TcpClient::new(
        TcpConfig::new(Endpoint::new("127.0.0.1", port).unwrap()).with_reconnect(
            ReconnectPolicy::default().with_initial_backoff(Duration::from_millis(10)),
        ),
    )
    .unwrap()
    .producer_with_handler(
        crate::config::ProducerOptions::new("group"),
        FailOncePublisherHandler(std::sync::atomic::AtomicBool::new(false)),
    )
    .await
    .unwrap();
    // Cancelling a wait must retain the running handler and connection tasks.
    assert!(
        tokio::time::timeout(Duration::from_millis(5), producer.join())
            .await
            .is_err()
    );
    tokio::time::timeout(Duration::from_secs(3), recovered_rx)
        .await
        .unwrap()
        .unwrap();
    producer.shutdown().await;
    producer.join().await.unwrap();
    server.await.unwrap();
}

#[tokio::test]
async fn http_roles_and_clones_reuse_one_connection_pool() {
    use crate::{
        config::{EndpointSet, HttpConfig, ProducerOptions},
        HttpClient,
    };
    use axum::{
        extract::{ConnectInfo, State},
        routing::post,
        Router,
    };
    use std::{collections::HashSet, net::SocketAddr};
    let (tx, mut rx) = tokio::sync::mpsc::unbounded_channel();
    let app = Router::new()
        .route(
            "/",
            post(
                |State(tx): State<tokio::sync::mpsc::UnboundedSender<SocketAddr>>,
                 ConnectInfo(peer): ConnectInfo<SocketAddr>| async move {
                    tx.send(peer).unwrap();
                    r#"{"retCode":0}"#
                },
            ),
        )
        .with_state(tx);
    let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let endpoint = Endpoint::new("127.0.0.1", listener.local_addr().unwrap().port()).unwrap();
    let server = tokio::spawn(async move {
        axum::serve(
            listener,
            app.into_make_service_with_connect_info::<SocketAddr>(),
        )
        .await
        .unwrap();
    });
    let client = HttpClient::new(HttpConfig::new(EndpointSet::new([endpoint]).unwrap())).unwrap();
    for role in [
        client.producer(ProducerOptions::new("a")).unwrap(),
        client.clone().producer(ProducerOptions::new("b")).unwrap(),
    ] {
        role.publish(
            crate::EventMeshMessage::new("orders", "payload")
                .unwrap()
                .into(),
        )
        .await
        .unwrap();
    }
    let registration = client
        .webhook_registration(ConsumerOptions::new("group"))
        .unwrap();
    registration
        .subscribe(
            Subscription::new("orders"),
            "http://127.0.0.1:8080/callback",
        )
        .await
        .unwrap();
    registration.close().await.unwrap();
    let mut peers = HashSet::new();
    for _ in 0..4 {
        peers.insert(rx.recv().await.unwrap());
    }
    assert_eq!(
        peers.len(),
        1,
        "role factories must reuse the client's pool"
    );
    server.abort();
}

#[tokio::test]
async fn http_roles_share_weighted_round_robin_sequence() {
    use crate::{
        config::{EndpointSet, HttpConfig, LoadBalance, ProducerOptions},
        HttpClient,
    };
    let (tx, mut rx) = tokio::sync::mpsc::unbounded_channel();
    let mut endpoints = Vec::new();
    let mut servers = Vec::new();
    for index in 0..2 {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        endpoints.push(Endpoint::new("127.0.0.1", listener.local_addr().unwrap().port()).unwrap());
        let tx = tx.clone();
        let app = axum::Router::new().route(
            "/",
            axum::routing::post(move || {
                let tx = tx.clone();
                async move {
                    tx.send(index).unwrap();
                    r#"{"retCode":0}"#
                }
            }),
        );
        servers.push(tokio::spawn(async move {
            axum::serve(listener, app).await.unwrap()
        }));
    }
    let client = HttpClient::new(
        HttpConfig::new(EndpointSet::new(endpoints).unwrap())
            .with_load_balance(LoadBalance::WeightedRoundRobin),
    )
    .unwrap();
    for index in 0..4 {
        client
            .clone()
            .producer(ProducerOptions::new(format!("group-{index}")))
            .unwrap()
            .publish(
                crate::EventMeshMessage::new("orders", "payload")
                    .unwrap()
                    .into(),
            )
            .await
            .unwrap();
        assert_eq!(rx.recv().await.unwrap(), index % 2);
    }
    for server in servers {
        server.abort();
    }
}
