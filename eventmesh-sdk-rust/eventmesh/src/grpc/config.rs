use crate::constants;

use super::eventmesh_grpc::RequestHeader;

#[derive(Debug, Clone)]
pub struct EventMeshGrpcConfig {
    pub eventmesh_addr: String,
    pub env: String,
    pub idc: String,
    pub ip: String,
    pub pid: String,
    pub sys: String,
    pub user_name: String,
    pub password: String,
    pub producer_group: String,
    pub consumer_group:String
}

impl EventMeshGrpcConfig {
    pub(crate) fn build_header(&self) -> RequestHeader {
        RequestHeader {
            env: self.env.to_string(),
            region: String::from(""),
            idc: self.idc.to_string(),
            ip: self.ip.to_string(),
            pid: self.pid.to_string(),
            sys: self.sys.to_string(),
            username: self.user_name.to_string(),
            password: self.password.to_string(),
            language: String::from("RUST"),
            //TODO: cloudevent openmessage
            protocol_type: String::from(constants::EM_MESSAGE_PROTOCOL),
            protocol_version: String::from("1.0"),
            protocol_desc: String::from("grpc"),
        }
    }
}
