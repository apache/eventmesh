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

//! Compiles the EventMesh gRPC service protos (client stubs only).

fn main() -> Result<(), Box<dyn std::error::Error>> {
    // Only generate when the grpc feature is enabled at build time. The protos
    // are always present, but we skip codegen to avoid pulling tonic/prost when
    // the consumer only wants the HTTP/TCP transports (added in later phases).
    if !cfg!(feature = "grpc") {
        return Ok(());
    }

    #[cfg(feature = "grpc")]
    tonic_build::configure()
        .build_server(false)
        .build_client(true)
        .protoc_arg("--experimental_allow_proto3_optional")
        .compile_protos(
            &[
                "proto/eventmesh-service.proto",
                "proto/eventmesh-cloudevents.proto",
            ],
            &["proto"],
        )?;

    println!("cargo:rerun-if-changed=proto/eventmesh-service.proto");
    println!("cargo:rerun-if-changed=proto/eventmesh-cloudevents.proto");
    Ok(())
}
