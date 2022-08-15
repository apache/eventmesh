pub mod eventmesh_grpc {
    tonic::include_proto!("eventmesh.common.protocol.grpc");
}

pub mod config;
pub mod producer;
pub mod consumer;