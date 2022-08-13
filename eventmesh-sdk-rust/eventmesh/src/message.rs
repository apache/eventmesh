use serde::Serialize;
use std::time::SystemTime;
#[derive(Debug, Clone, Serialize)]
pub struct EventMeshMessage {
    #[serde(rename="bizseqno")]
    pub biz_seq_no: String,
    #[serde(rename="uniqueid")]
    pub unique_id: String,
    pub topic: String,
    pub content: String,
    // pub prop: HashMap<String, String>,
    pub ttl: i32,
    // pub(crate) create_time: u64,
}
impl EventMeshMessage {
    pub fn new(biz_seq_no: &str, unique_id: &str, topic: &str, content: &str, ttl: i32) -> Self {
        Self {
            biz_seq_no: biz_seq_no.to_string(),
            unique_id: unique_id.to_string(),
            topic: topic.to_string(),
            content: content.to_string(),
            ttl,
            // create_time: SystemTime::now()
            //     .duration_since(SystemTime::UNIX_EPOCH)
            //     .unwrap()
            //     .as_millis()
            //     .try_into()
            //     .unwrap(),
        }
    }
}
