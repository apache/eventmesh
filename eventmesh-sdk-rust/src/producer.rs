use anyhow::Result;
pub trait Producer<Message, Config>: Sized {
    fn publish(message: &Message) -> Result<()>;
    fn new(config: &Config) -> Result<Self>;
}
