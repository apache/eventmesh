use std::collections::HashMap;
use std::time::SystemTime;
use serde::Serialize;
#[derive(Debug, Clone, Serialize)]
pub struct EventMeshMessage {
    pub biz_seq_no: String,
    pub unique_id: String,
    pub topic: String,
    pub content: String,
    pub prop: HashMap<String, String>,
    pub(crate) create_time: u64,
}
impl Default for EventMeshMessage {
    fn default() -> Self {
        Self {
            biz_seq_no: Default::default(),
            unique_id: Default::default(),
            topic: Default::default(),
            content: Default::default(),
            prop: Default::default(),
            create_time: SystemTime::now()
                .duration_since(SystemTime::UNIX_EPOCH)
                .unwrap()
                .as_millis()
                .try_into()
                .unwrap(),
        }
    }
}
