pub mod avro_example;
pub mod avro_ex_doc;
pub mod defaults;
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt};

#[tokio::main]
pub async fn main() -> Result<(), Box<dyn std::error::Error>> {
    // tracing_subscriber::fmt()
    //     .with_env_filter(tracing_subscriber::EnvFilter::from_default_env())
    //     .with_target(true)
    //     .compact()
    //     .init();
    tracing_subscriber::registry()
        .with(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "info".into()),
        )
        .with(tracing_subscriber::fmt::layer())
        .init();

    let port = defaults::PORT;
    let addr = format!("[::]:{}", port).parse::<std::net::SocketAddr>()?;

    tracing::info!("Datorum server starting on {} (HTTP/3)", addr);
    println!("Datorum server starting on {} (HTTP/3)", addr);

    // TODO Start HTTP/3 server task

    // Demonstrate Avro schemas (can be removed or gated by a feature later)
    avro_ex_doc::test_schemas();

    // Wait for Ctrl+C for graceful shutdown
    tokio::signal::ctrl_c().await?;
    tracing::info!("Shutdown signal received. Stopping server...");

    println!("Shutdown signal received. Stopping server...");

    // TODO Abort HTTP/3 server task (simplistic). Could implement graceful shutdown with channels.

    Ok(())
}
