#[macro_export] macro_rules! const_str {
    ($key: ident,$value:expr) => {
        pub(crate) const $key: &str = $value;
    };
}