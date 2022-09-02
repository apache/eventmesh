#[macro_export] macro_rules! const_str {
    ($key: ident,$value:expr) => {
        pub(crate) const $key: &str = $value;
    };
}
const_str!(EM_MESSAGE_PROTOCOL, "eventmeshmessage");
const_str!(PROTOCOL_DESC, "http");
