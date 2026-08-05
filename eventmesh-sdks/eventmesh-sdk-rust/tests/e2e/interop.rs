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

//! Cross-SDK gRPC, HTTP, and TCP interoperability with a Java SDK peer.

use std::{path::PathBuf, process::Command, sync::OnceLock, time::Duration};

use eventmesh::message::{EventMeshMessage, Message};
use tokio::{
    io::{AsyncBufReadExt, AsyncRead, BufReader, Lines},
    process::Command as TokioCommand,
};

use crate::{
    harness::{
        ensure_topic, grpc_producer, http_producer, http_warm_topic, let_stream_settle,
        serialize_tcp_e2e, tcp_producer, tcp_warm_topic, unique_topic, wait_for_tcp_topic_listener,
        warm_topic,
    },
    require_runtime,
    runtime::webhook_host,
};

static PEER_JAR: OnceLock<PathBuf> = OnceLock::new();

fn java_peer_dir() -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("interop/java-peer")
}

fn peer_jar() -> &'static PathBuf {
    PEER_JAR.get_or_init(|| {
        let project = java_peer_dir();
        let output = Command::new("mvn")
            .current_dir(&project)
            .args(["--quiet", "--batch-mode", "-DskipTests", "package"])
            .output()
            .expect("run Maven for Java interop peer");
        assert!(
            output.status.success(),
            "build Java interop peer: {}",
            String::from_utf8_lossy(&output.stderr)
        );
        let jar = project.join("target/eventmesh-java-interop-peer.jar");
        assert!(
            jar.is_file(),
            "Java interop peer jar was not created at {}",
            jar.display()
        );
        jar
    })
}

async fn peer(operation: &str, topic: &str, argument: Option<&str>) -> tokio::process::Child {
    let mut command = TokioCommand::new("java");
    command.args([
        "-jar",
        peer_jar().to_str().expect("utf-8 Java peer jar path"),
        operation,
        "127.0.0.1",
        topic,
    ]);
    if let Some(argument) = argument {
        command.arg(argument);
    }
    command
        .stdout(std::process::Stdio::piped())
        .stderr(std::process::Stdio::inherit())
        .spawn()
        .expect("start Java SDK peer")
}

async fn wait_for_line<R: AsyncRead + Unpin>(lines: &mut Lines<BufReader<R>>, expected: &str) {
    let deadline = tokio::time::Instant::now() + Duration::from_secs(30);
    loop {
        let remaining = deadline.saturating_duration_since(tokio::time::Instant::now());
        let line = tokio::time::timeout(remaining, lines.next_line())
            .await
            .expect("Java peer output timeout")
            .expect("read Java peer output")
            .expect("Java peer exited before expected output");
        if line == expected {
            return;
        }
    }
}

async fn assert_peer_success(child: &mut tokio::process::Child) {
    assert!(child.wait().await.expect("wait Java peer").success());
}

#[tokio::test(flavor = "multi_thread")]
async fn grpc_rust_publishes_to_java_consumer() {
    require_runtime!();
    let topic = unique_topic("interop-grpc-rust-java");
    ensure_topic(&topic).await;
    let mut child = peer("grpc-consume", &topic, None).await;
    let mut lines = BufReader::new(child.stdout.take().expect("peer stdout")).lines();
    wait_for_line(&mut lines, "INTEROP_READY").await;
    grpc_producer()
        .publish(Message::from(
            EventMeshMessage::new(&topic, "from-rust-grpc").unwrap(),
        ))
        .await
        .expect("Rust gRPC publish");
    wait_for_line(
        &mut lines,
        &format!("INTEROP_RECEIVED={topic}\tfrom-rust-grpc"),
    )
    .await;
    assert_peer_success(&mut child).await;
}

#[tokio::test(flavor = "multi_thread")]
async fn grpc_java_publishes_to_rust_consumer() {
    require_runtime!();
    let topic = unique_topic("interop-grpc-java-rust");
    ensure_topic(&topic).await;
    let (_consumer, mut receiver) = warm_topic(&topic).await;
    let mut child = peer("grpc-publish", &topic, Some("from-java-grpc")).await;
    assert_peer_success(&mut child).await;
    let received = tokio::time::timeout(Duration::from_secs(20), receiver.recv())
        .await
        .expect("Rust gRPC receive timeout")
        .expect("Rust gRPC listener closed");
    assert_eq!(received.content(), "from-java-grpc");
}

#[tokio::test(flavor = "multi_thread")]
async fn http_rust_publishes_to_java_consumer() {
    require_runtime!();
    let topic = unique_topic("interop-http-rust-java");
    ensure_topic(&topic).await;
    let callback_host = webhook_host();
    let mut child = peer("http-consume", &topic, Some(&callback_host)).await;
    let mut lines = BufReader::new(child.stdout.take().expect("peer stdout")).lines();
    wait_for_line(&mut lines, "INTEROP_READY").await;
    let_stream_settle().await;
    http_producer()
        .publish(Message::from(
            EventMeshMessage::new(&topic, "from-rust-http").unwrap(),
        ))
        .await
        .expect("Rust HTTP publish");
    wait_for_line(
        &mut lines,
        &format!("INTEROP_RECEIVED={topic}\tfrom-rust-http"),
    )
    .await;
    assert_peer_success(&mut child).await;
}

#[tokio::test(flavor = "multi_thread")]
async fn http_java_publishes_to_rust_consumer() {
    require_runtime!();
    let topic = unique_topic("interop-http-java-rust");
    ensure_topic(&topic).await;
    let (consumer, mut receiver) = http_warm_topic(&topic).await;
    let mut child = peer("http-publish", &topic, Some("from-java-http")).await;
    assert_peer_success(&mut child).await;
    let received = tokio::time::timeout(Duration::from_secs(20), receiver.recv())
        .await
        .expect("Rust HTTP receive timeout")
        .expect("Rust HTTP listener closed");
    assert_eq!(received.content(), "from-java-http");
    consumer.close().await.expect("close Rust HTTP consumer");
}

#[tokio::test(flavor = "multi_thread")]
async fn tcp_rust_publishes_to_java_consumer() {
    let _tcp_e2e_guard = serialize_tcp_e2e().await;
    require_runtime!();
    let topic = unique_topic("interop-tcp-rust-java");
    ensure_topic(&topic).await;
    let mut child = peer("tcp-consume", &topic, None).await;
    let mut lines = BufReader::new(child.stdout.take().expect("peer stdout")).lines();
    wait_for_line(&mut lines, "INTEROP_READY").await;
    wait_for_tcp_topic_listener(&topic, true).await;
    let producer = tcp_producer().await;
    producer
        .publish(Message::from(
            EventMeshMessage::new(&topic, "from-rust-tcp").unwrap(),
        ))
        .await
        .expect("Rust TCP publish");
    wait_for_line(
        &mut lines,
        &format!("INTEROP_RECEIVED={topic}\tfrom-rust-tcp"),
    )
    .await;
    assert_peer_success(&mut child).await;
    producer.shutdown().await;
}

#[tokio::test(flavor = "multi_thread")]
async fn tcp_java_publishes_to_rust_consumer() {
    let _tcp_e2e_guard = serialize_tcp_e2e().await;
    require_runtime!();
    let topic = unique_topic("interop-tcp-java-rust");
    ensure_topic(&topic).await;
    let (consumer, mut receiver) = tcp_warm_topic(&topic).await;
    let mut child = peer("tcp-publish", &topic, Some("from-java-tcp")).await;
    assert_peer_success(&mut child).await;
    let received = tokio::time::timeout(Duration::from_secs(20), receiver.recv())
        .await
        .expect("Rust TCP receive timeout")
        .expect("Rust TCP listener closed");
    assert_eq!(received.content(), "from-java-tcp");
    consumer.shutdown();
    consumer.join().await.expect("join Rust TCP consumer");
}
