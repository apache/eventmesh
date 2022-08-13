#[derive(Debug, Clone)]
pub struct EventMeshHttpConfig {
    pub eventmesh_addr: String,
    pub env: String,
    pub idc: String,
    pub ip: String,
    pub pid: String,
    pub sys: String,
    pub user_name: String,
    pub password: String,
    pub producergroup: String,
}
