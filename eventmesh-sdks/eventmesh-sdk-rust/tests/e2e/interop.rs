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

//! Cross-SDK gRPC interoperability with a Java SDK peer started on the host.

use std::{path::PathBuf, process::Command, sync::OnceLock, time::Duration};

use eventmesh::message::{EventMeshMessage, Message};
use tokio::{
    io::{AsyncBufReadExt, BufReader},
    process::Command as TokioCommand,
};

use crate::{
    harness::{ensure_topic, grpc_producer, unique_topic, warm_topic},
    require_runtime,
};

const PEER_CLASS: &str = "org.apache.eventmesh.client.interop.GrpcInteropPeer";
static CLASSPATH: OnceLock<String> = OnceLock::new();

fn java_sdk_dir() -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .parent()
        .expect("eventmesh-sdks directory")
        .join("eventmesh-sdk-java")
}

fn peer_classpath() -> &'static String {
    CLASSPATH.get_or_init(|| {
        let java_sdk = java_sdk_dir();
        let root = java_sdk.parent().expect("eventmesh-sdks parent").parent().expect("repo root");
        let run = |proxy: bool| {
            let mut cmd = Command::new(root.join("gradlew"));
            cmd.current_dir(root).args(["--no-daemon", "-q", ":eventmesh-sdks:eventmesh-sdk-java:interopPeerClasspath"]);
            if proxy {
                cmd.env("GRADLE_OPTS", "-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890");
            }
            cmd.output().expect("run Gradle for Java interop peer")
        };
        let mut output = run(false);
        if !output.status.success() {
            output = run(true);
        }
        assert!(output.status.success(), "build Java interop peer: {}", String::from_utf8_lossy(&output.stderr));
        String::from_utf8(output.stdout).expect("Gradle stdout").lines()
            .find_map(|line| line.strip_prefix("INTEROP_PEER_CLASSPATH=")).expect("Java peer classpath").to_owned()
    })
}

async fn peer(operation: &str, topic: &str, content: Option<&str>) -> tokio::process::Child {
    let mut command = TokioCommand::new("java");
    command.args([
        "-cp",
        peer_classpath(),
        PEER_CLASS,
        operation,
        "127.0.0.1",
        topic,
    ]);
    if let Some(content) = content {
        command.arg(content);
    }
    command
        .stdout(std::process::Stdio::piped())
        .stderr(std::process::Stdio::inherit())
        .spawn()
        .expect("start Java SDK peer")
}

#[tokio::test(flavor = "multi_thread")]
async fn rust_publishes_to_java_consumer() {
    require_runtime!();
    let topic = unique_topic("interop-rust-java");
    ensure_topic(&topic).await;
    let mut child = peer("consume", &topic, None).await;
    tokio::time::sleep(Duration::from_secs(2)).await;
    grpc_producer()
        .publish(Message::from(
            EventMeshMessage::new(&topic, "from-rust").unwrap(),
        ))
        .await
        .expect("Rust publish");
    let mut lines = BufReader::new(child.stdout.take().expect("peer stdout")).lines();
    let expected = format!("INTEROP_RECEIVED={topic}\tfrom-rust");
    let deadline = tokio::time::Instant::now() + Duration::from_secs(20);
    loop {
        let remaining = deadline.saturating_duration_since(tokio::time::Instant::now());
        let line = tokio::time::timeout(remaining, lines.next_line())
            .await
            .expect("Java receive timeout")
            .expect("peer output")
            .expect("peer exited before receiving message");
        if line == expected {
            break;
        }
    }
    assert!(child.wait().await.expect("wait Java peer").success());
}

#[tokio::test(flavor = "multi_thread")]
async fn java_publishes_to_rust_consumer() {
    require_runtime!();
    let topic = unique_topic("interop-java-rust");
    ensure_topic(&topic).await;
    let (_consumer, mut receiver) = warm_topic(&topic).await;
    let mut child = peer("publish", &topic, Some("from-java")).await;
    assert!(child.wait().await.expect("wait Java peer").success());
    let received = tokio::time::timeout(Duration::from_secs(20), receiver.recv())
        .await
        .expect("Rust receive timeout")
        .expect("Rust listener closed");
    assert_eq!(received.content(), "from-java");
}
