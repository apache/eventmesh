use super::{config::EventMeshHttpConfig, protocol_key};
use crate::{
    http::protocol_const,
    message::EventMeshMessage,
    producer::{self, Producer},
};
use anyhow::Result;
use reqwest::header::{self, HeaderValue};
use serde::Serialize;

pub struct EventMeshMessageProducer {
    client: reqwest::Client,
}
macro_rules! header {
    ($header:expr,$key:expr, $value:expr) => {
        $header.insert($key, HeaderValue::from_str(&$value)?);
    };
}
#[derive(Serialize)]
struct Body {
    producergroup: String,
    #[serde(flatten)]
    body: EventMeshMessage
}
impl Producer<EventMeshMessage, EventMeshHttpConfig> for EventMeshMessageProducer {
    fn publish(message: &EventMeshMessage) -> Result<()> {
        todo!()
    }

    fn new(config: &EventMeshHttpConfig) -> Result<Self> {
        let mut headers = header::HeaderMap::with_capacity(12);

        header!(headers, protocol_key::CLIENT_ENV, config.env);
        header!(headers, protocol_key::CLIENT_IDC, config.idc);
        header!(headers, protocol_key::CLIENT_IP, config.ip);
        header!(headers, protocol_key::CLIENT_PID, config.pid);
        header!(headers, protocol_key::CLIENT_SYS, config.sys);
        header!(headers, protocol_key::CLIENT_USERNAME, config.user_name);
        header!(headers, protocol_key::CLIENT_PASSWD, config.password);
        header!(
            headers,
            protocol_key::PROTOCOL_TYPE,
            protocol_const::EM_MESSAGE_PROTOCOL
        );
        header!(
            headers,
            protocol_key::RPOTOCOL_DESC,
            protocol_const::PROTOCOL_DESC
        );
        // SpecVersion::V03 => "0.3",
        // SpecVersion::V10 => "1.0",
        header!(headers, protocol_key::PROTOCOL_VERSION, "1.0");
        header!(headers, protocol_key::LANGUAGE, "rust");
        let client = reqwest::ClientBuilder::new().default_headers(headers);
        Ok(EventMeshMessageProducer {
            client: client.build()?,
        })
    }
}
