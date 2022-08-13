use anyhow::Result;
pub trait Producer<Message, Config>: Sized {
    fn publish(&self, message: Message) -> Result<()>;
    fn new(config: &Config) -> Result<Self>;
}
