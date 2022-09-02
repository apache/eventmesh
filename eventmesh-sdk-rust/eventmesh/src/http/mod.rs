pub mod config;
pub mod producer;
mod protocol_key;
mod request_code;
mod protocol_version {
    use std::fmt::Display;

    #[derive(Debug, Clone, Copy)]
    pub(super) enum ProtocolVersion {
        V1,
        V2,
    }
    impl Display for ProtocolVersion {
        fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
            match self {
                ProtocolVersion::V1 => write!(f, "1.0"),
                ProtocolVersion::V2 => write!(f, "2.0"),
            }
        }
    }
}
