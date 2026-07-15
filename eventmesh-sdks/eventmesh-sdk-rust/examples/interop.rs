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

//! Standalone interop test binary for issue #7 verification.
//!
//! Usage:
//!   cargo run --example interop --features full -- publish   <topic> <content>
//!   cargo run --example interop --features full -- consume   <topic> <timeout_sec>
//!   cargo run --example interop --features full -- pub_props <topic>

use std::time::Duration;

use eventmesh::{
    config::GrpcClientConfig,
    grpc::{GrpcProducer, GrpcStreamConsumer},
    model::{EventMeshMessage, SubscriptionItem, SubscriptionMode, SubscriptionType},
    transport::Publisher,
    MessageListener,
};

const HOST: &str = "127.0.0.1";
const GRPC_PORT: u16 = 10_205;

fn producer_config(group: &str) -> GrpcClientConfig {
    GrpcClientConfig::builder()
        .server_addr(HOST)
        .server_port(GRPC_PORT)
        .env("env")
        .idc("idc")
        .sys("sys")
        .username("eventmesh")
        .password("eventmesh")
        .producer_group(group)
        .build()
}

fn consumer_config(group: &str) -> GrpcClientConfig {
    GrpcClientConfig::builder()
        .server_addr(HOST)
        .server_port(GRPC_PORT)
        .env("env")
        .idc("idc")
        .sys("sys")
        .username("eventmesh")
        .password("eventmesh")
        .consumer_group(group)
        .build()
}

/// Collecting listener that prints received messages.
struct PrintListener;
impl MessageListener for PrintListener {
    type Message = EventMeshMessage;
    async fn handle(&self, msg: EventMeshMessage) -> Option<EventMeshMessage> {
        println!(
            "RUST_RECEIVED topic={:?} content={:?} props={:?}",
            msg.topic, msg.content, msg.props
        );
        None
    }
}

#[tokio::main(flavor = "multi_thread")]
async fn main() {
    let args: Vec<String> = std::env::args().collect();
    if args.len() < 2 {
        eprintln!("Usage: interop <publish|consume|pub_props> ...");
        std::process::exit(1);
    }
    let mode = &args[1];

    match mode.as_str() {
        "publish" => {
            let topic = &args[2];
            let content = &args[3];
            let producer = GrpcProducer::connect(producer_config("rust-interop-pub"))
                .expect("connect producer");
            let msg = EventMeshMessage::builder()
                .topic(topic)
                .content(content)
                .biz_seq_no("rust-seq-001")
                .unique_id("rust-uid-001")
                .build();
            let resp = producer.publish(msg).await.expect("publish");
            println!(
                "RUST_PUBLISH_RESP code={:?} msg={:?}",
                resp.code, resp.message
            );
            println!("RUST_PUBLISH_DONE");
            drop(producer);
        }
        "consume" => {
            let topic = args[2].clone();
            let timeout_sec: u64 = args[3].parse().unwrap_or(30);
            let consumer = GrpcStreamConsumer::subscribe_stream(
                consumer_config("rust-interop-sub"),
                PrintListener,
                vec![SubscriptionItem::new(
                    &topic,
                    SubscriptionMode::CLUSTERING,
                    SubscriptionType::ASYNC,
                )],
                None::<std::future::Ready<()>>,
            )
            .await
            .expect("subscribe_stream");

            println!("RUST_CONSUMING topic={}", topic);
            tokio::select! {
                _ = tokio::time::sleep(Duration::from_secs(timeout_sec)) => {
                    println!("RUST_TIMEOUT");
                    std::process::exit(2);
                }
                _ = consumer.wait_for_shutdown() => {}
            }
        }
        "pub_props" => {
            let topic = &args[2];
            let producer = GrpcProducer::connect(producer_config("rust-interop-props"))
                .expect("connect producer");
            // Issue 7b: publish with conflicting reserved keys in props.
            // The typed ttl is 7000, but we also set stale "ttl" prop of 99000.
            // With the fix, the server should use 7000, not 99000.
            let msg = EventMeshMessage::builder()
                .topic(topic)
                .content("props-test-from-rust")
                .biz_seq_no("rust-props-seq")
                .unique_id("rust-props-uid")
                .ttl_millis(7_000)
                .prop("ttl", "99000")
                .prop("customprop", "should-survive")
                .prop("datacontenttype", "text/plain")
                .build();
            let resp = producer.publish(msg).await.expect("publish");
            println!(
                "RUST_PROPS_RESP code={:?} msg={:?}",
                resp.code, resp.message
            );
            println!("RUST_PROPS_DONE");
            drop(producer);
        }
        _ => {
            eprintln!("Unknown mode: {}", mode);
            std::process::exit(1);
        }
    }
}
