use std::fmt::Display;

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum RequestCode {
    MsgBatchSend,
    MsgBatchSendV2,
    MsgSendSync,
    MsgSendAsync,
    HttpPushClientAsync,
    HttpPushClientSync,
    Register,
    Unregister,
    Heartbeat,
    Subscribe,
    ReplyMessage,
    Unsubscribe,
    AdminMetrics,
    AdminShutdown,
}
impl Display for RequestCode {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            RequestCode::MsgBatchSend => write!(f, "SEND BATCH MSG"),
            RequestCode::MsgBatchSendV2 => write!(f, "SEND BATCH MSG V2"),
            RequestCode::MsgSendSync => write!(f, "SEND SINGLE MSG SYNC"),
            RequestCode::MsgSendAsync => write!(f, "SEND SINGLE MSG ASYNC"),
            RequestCode::HttpPushClientAsync => write!(f, "PUSH CLIENT BY HTTP POST"),
            RequestCode::HttpPushClientSync => write!(f, "PUSH CLIENT BY HTTP POST"),
            RequestCode::Register => write!(f, "REGISTER"),
            RequestCode::Unregister => write!(f, "UNREGISTER"),
            RequestCode::Heartbeat => write!(f, "HEARTBEAT"),
            RequestCode::Subscribe => write!(f, "SUBSCRIBE"),
            RequestCode::ReplyMessage => write!(f, "REPLY MESSAGE"),
            RequestCode::Unsubscribe => write!(f, "UNSUBSCRIBE"),
            RequestCode::AdminMetrics => write!(f, "ADMIN METRICS"),
            RequestCode::AdminShutdown => write!(f,"ADMIN SHUTDOWN"),
        }
    }
}
impl Into<i32> for RequestCode {
    fn into(self) -> i32 {
        match self {
            RequestCode::MsgBatchSend => 102,
            RequestCode::MsgBatchSendV2 => 107,
            RequestCode::MsgSendSync => 101,
            RequestCode::MsgSendAsync => 104,
            RequestCode::HttpPushClientAsync => 105,
            RequestCode::HttpPushClientSync => 106,
            RequestCode::Register => 201,
            RequestCode::Unregister => 202,
            RequestCode::Heartbeat => 203,
            RequestCode::Subscribe => 206,
            RequestCode::ReplyMessage => 301,
            RequestCode::Unsubscribe => 207,
            RequestCode::AdminMetrics => 603,
            RequestCode::AdminShutdown => 601,
        }
    }
}
