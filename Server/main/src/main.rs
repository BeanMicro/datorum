pub mod defaults;
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt};

#[tokio::main]
pub async fn main() -> Result<(), Box<dyn std::error::Error>> {
    println!("Hello World");

    // TODO Abort HTTP/3 server task (simplistic). Could implement graceful shutdown with channels.

    Ok(())
}
